/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <algorithm>
#include <cerrno>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <dirent.h>
#include <fcntl.h>
#include <getopt.h>
#include <limits>
#include <string>
#include <string_view>
#include <sys/stat.h>
#include <sys/sysmacros.h>
#include <sys/types.h>
#include <unistd.h>
#include <utility>
#include <vector>

namespace {

constexpr char kTrailer[] = "TRAILER!!!";
constexpr uint32_t kFirstInode = 300000;
constexpr size_t kCopyBufferSize = 64 * 1024;

uint64_t total_size = 0;
uint32_t next_inode = kFirstInode;

struct CannedConfigEntry {
    std::string path;
    mode_t mode;
};

struct DeviceNode {
    std::string path;
    mode_t mode;
    dev_t device;
};

std::vector<CannedConfigEntry> canned_config;
bool has_canned_config = false;

[[noreturn]] void die(const char* format, ...) {
    va_list args;
    va_start(args, format);
    std::fputs("mkbootfs: ", stderr);
    std::vfprintf(stderr, format, args);
    std::fputc('\n', stderr);
    va_end(args);
    std::exit(EXIT_FAILURE);
}

[[noreturn]] void die_errno(const char* operation, const std::string& path) {
    die("%s '%s': %s", operation, path.c_str(), std::strerror(errno));
}

void write_stdout(const void* data, size_t size) {
    if (size == 0) {
        return;
    }
    if (std::fwrite(data, 1, size, stdout) != size) {
        die("failed writing archive: %s", std::strerror(errno));
    }
    total_size += size;
}

void pad_to(size_t alignment) {
    static constexpr char zeros[256] = {};
    const size_t padding = static_cast<size_t>((alignment - total_size % alignment) % alignment);
    write_stdout(zeros, padding);
}

std::vector<std::string_view> split_fields(std::string_view line) {
    std::vector<std::string_view> fields;
    size_t cursor = 0;
    while (cursor < line.size()) {
        while (cursor < line.size() &&
               (line[cursor] == ' ' || line[cursor] == '\t' || line[cursor] == '\r' ||
                line[cursor] == '\n')) {
            ++cursor;
        }
        if (cursor == line.size() || line[cursor] == '#') {
            break;
        }
        const size_t start = cursor;
        while (cursor < line.size() && line[cursor] != ' ' && line[cursor] != '\t' &&
               line[cursor] != '\r' && line[cursor] != '\n') {
            ++cursor;
        }
        fields.emplace_back(line.substr(start, cursor - start));
    }
    return fields;
}

unsigned long parse_unsigned(std::string_view value, int base, const char* field,
                             const std::string& source, size_t line_number) {
    std::string owned(value);
    char* end = nullptr;
    errno = 0;
    const unsigned long result = std::strtoul(owned.c_str(), &end, base);
    if (errno != 0 || end == owned.c_str() || *end != '\0') {
        die("invalid %s in '%s' at line %zu", field, source.c_str(), line_number);
    }
    return result;
}

mode_t archive_mode(const std::string& path, mode_t source_mode) {
    if (!has_canned_config) {
        return source_mode;
    }

    const CannedConfigEntry* fallback = nullptr;
    for (const auto& entry : canned_config) {
        if (entry.path.empty()) {
            fallback = &entry;
        } else if (entry.path == path) {
            return (source_mode & ~static_cast<mode_t>(07777)) | entry.mode;
        }
    }

    if (fallback == nullptr) {
        die("canned configuration is missing its default entry");
    }
    return (source_mode & ~static_cast<mode_t>(07777)) | fallback->mode;
}

void emit_header(mode_t mode, dev_t device, const std::string& output_path, uint64_t data_size) {
    if (output_path.size() >= std::numeric_limits<uint32_t>::max()) {
        die("archive path is too long: '%s'", output_path.c_str());
    }
    if (data_size > std::numeric_limits<uint32_t>::max()) {
        die("file is too large for newc format: '%s'", output_path.c_str());
    }

    pad_to(4);

    const uint32_t rdev_major =
            (S_ISBLK(mode) || S_ISCHR(mode)) ? static_cast<uint32_t>(major(device)) : 0;
    const uint32_t rdev_minor =
            (S_ISBLK(mode) || S_ISCHR(mode)) ? static_cast<uint32_t>(minor(device)) : 0;
    const uint32_t name_size = static_cast<uint32_t>(output_path.size() + 1);

    char header[111];
    const int length = std::snprintf(
            header, sizeof(header),
            "070701%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x",
            next_inode++, static_cast<uint32_t>(mode), 0U, 0U, 1U, 0U,
            static_cast<uint32_t>(data_size), 0U, 0U, rdev_major, rdev_minor, name_size, 0U);
    if (length != 110) {
        die("failed to encode newc header for '%s'", output_path.c_str());
    }

    write_stdout(header, static_cast<size_t>(length));
    write_stdout(output_path.c_str(), output_path.size() + 1);
    pad_to(4);
}

void emit_memory_entry(mode_t source_mode, dev_t device, const std::string& output_path,
                       const void* data, size_t data_size) {
    const mode_t mode = archive_mode(output_path, source_mode);
    emit_header(mode, device, output_path, data_size);
    write_stdout(data, data_size);
    pad_to(4);
}

void emit_file_entry(const struct stat& file_stat, const std::string& input_path,
                     const std::string& output_path) {
    if (file_stat.st_size < 0) {
        die("negative file size for '%s'", input_path.c_str());
    }
    const uint64_t file_size = static_cast<uint64_t>(file_stat.st_size);
    const mode_t mode = archive_mode(output_path, file_stat.st_mode);
    emit_header(mode, 0, output_path, file_size);

    const int fd = open(input_path.c_str(), O_RDONLY | O_CLOEXEC | O_NOFOLLOW);
    if (fd < 0) {
        die_errno("cannot open", input_path);
    }

    std::vector<char> buffer(kCopyBufferSize);
    uint64_t remaining = file_size;
    while (remaining > 0) {
        const size_t request =
                static_cast<size_t>(std::min<uint64_t>(remaining, buffer.size()));
        ssize_t count;
        do {
            count = read(fd, buffer.data(), request);
        } while (count < 0 && errno == EINTR);

        if (count < 0) {
            const int saved_errno = errno;
            close(fd);
            errno = saved_errno;
            die_errno("cannot read", input_path);
        }
        if (count == 0) {
            close(fd);
            die("unexpected end of file while reading '%s'", input_path.c_str());
        }

        write_stdout(buffer.data(), static_cast<size_t>(count));
        remaining -= static_cast<uint64_t>(count);
    }

    if (close(fd) != 0) {
        die_errno("cannot close", input_path);
    }
    pad_to(4);
}

std::vector<char> read_symlink(const std::string& path, const struct stat& file_stat) {
    size_t capacity = file_stat.st_size > 0 ? static_cast<size_t>(file_stat.st_size) + 1 : 256;
    std::vector<char> target(capacity);

    while (true) {
        const ssize_t length = readlink(path.c_str(), target.data(), target.size());
        if (length < 0) {
            die_errno("cannot read symlink", path);
        }
        if (static_cast<size_t>(length) < target.size()) {
            target.resize(static_cast<size_t>(length));
            return target;
        }
        if (target.size() > std::numeric_limits<uint32_t>::max() / 2U) {
            die("symlink target is too long: '%s'", path.c_str());
        }
        target.resize(target.size() * 2U);
    }
}

std::string join_path(const std::string& parent, const std::string& name) {
    if (parent.empty()) {
        return name;
    }
    if (parent.back() == '/') {
        return parent + name;
    }
    return parent + "/" + name;
}

void archive_path(const std::string& input_path, const std::string& output_path);

void archive_directory(const std::string& input_path, const std::string& output_path) {
    DIR* directory = opendir(input_path.c_str());
    if (directory == nullptr) {
        die_errno("cannot open directory", input_path);
    }

    std::vector<std::string> names;
    errno = 0;
    while (dirent* entry = readdir(directory)) {
        if (std::strcmp(entry->d_name, ".") == 0 || std::strcmp(entry->d_name, "..") == 0) {
            continue;
        }
        names.emplace_back(entry->d_name);
    }
    const int read_error = errno;
    if (closedir(directory) != 0 && read_error == 0) {
        die_errno("cannot close directory", input_path);
    }
    if (read_error != 0) {
        errno = read_error;
        die_errno("cannot read directory", input_path);
    }

    std::sort(names.begin(), names.end());
    for (const auto& name : names) {
        archive_path(join_path(input_path, name), join_path(output_path, name));
    }
}

void archive_path(const std::string& input_path, const std::string& output_path) {
    struct stat file_stat {};
    if (lstat(input_path.c_str(), &file_stat) != 0) {
        die_errno("cannot stat", input_path);
    }

    if (S_ISREG(file_stat.st_mode)) {
        emit_file_entry(file_stat, input_path, output_path);
    } else if (S_ISDIR(file_stat.st_mode)) {
        emit_memory_entry(file_stat.st_mode, 0, output_path, nullptr, 0);
        archive_directory(input_path, output_path);
    } else if (S_ISLNK(file_stat.st_mode)) {
        const auto target = read_symlink(input_path, file_stat);
        emit_memory_entry(file_stat.st_mode, 0, output_path, target.data(), target.size());
    } else if (S_ISBLK(file_stat.st_mode) || S_ISCHR(file_stat.st_mode) ||
               S_ISFIFO(file_stat.st_mode) || S_ISSOCK(file_stat.st_mode)) {
        emit_memory_entry(file_stat.st_mode, file_stat.st_rdev, output_path, nullptr, 0);
    } else {
        die("unsupported file type for '%s' (mode %o)", input_path.c_str(), file_stat.st_mode);
    }
}

void read_canned_config(const std::string& filename) {
    FILE* file = std::fopen(filename.c_str(), "re");
    if (file == nullptr) {
        die_errno("cannot open canned configuration", filename);
    }

    char* line = nullptr;
    size_t capacity = 0;
    size_t line_number = 0;
    bool has_default = false;
    while (getline(&line, &capacity, file) != -1) {
        ++line_number;
        const std::string_view line_view(line);
        const bool default_entry =
                !line_view.empty() && (line_view.front() == ' ' || line_view.front() == '\t');
        const auto fields = split_fields(line_view);
        if (fields.empty()) {
            continue;
        }

        const size_t expected_fields = default_entry ? 3 : 4;
        if (fields.size() != expected_fields) {
            std::free(line);
            std::fclose(file);
            die("invalid canned configuration '%s' at line %zu", filename.c_str(), line_number);
        }

        const size_t offset = default_entry ? 0 : 1;
        (void)parse_unsigned(fields[offset], 10, "uid", filename, line_number);
        (void)parse_unsigned(fields[offset + 1], 10, "gid", filename, line_number);
        const unsigned long parsed_mode =
                parse_unsigned(fields[offset + 2], 8, "mode", filename, line_number);
        if (parsed_mode > 07777UL) {
            std::free(line);
            std::fclose(file);
            die("mode exceeds 07777 in '%s' at line %zu", filename.c_str(), line_number);
        }

        std::string path = default_entry ? std::string() : std::string(fields.front());
        if (path.empty()) {
            if (has_default) {
                std::free(line);
                std::fclose(file);
                die("duplicate default entry in '%s'", filename.c_str());
            }
            has_default = true;
        }
        canned_config.push_back({std::move(path), static_cast<mode_t>(parsed_mode)});
    }

    const int read_error = std::ferror(file) ? (errno == 0 ? EIO : errno) : 0;
    std::free(line);
    if (std::fclose(file) != 0 && read_error == 0) {
        die_errno("cannot close canned configuration", filename);
    }
    if (read_error != 0) {
        errno = read_error;
        die_errno("cannot read canned configuration", filename);
    }
    if (!has_default) {
        die("canned configuration '%s' is missing its default entry", filename.c_str());
    }
    has_canned_config = true;
}

std::vector<DeviceNode> read_device_nodes(const std::string& filename) {
    FILE* file = std::fopen(filename.c_str(), "re");
    if (file == nullptr) {
        die_errno("cannot open device-node description", filename);
    }

    std::vector<DeviceNode> nodes;
    char* line = nullptr;
    size_t capacity = 0;
    size_t line_number = 0;
    while (getline(&line, &capacity, file) != -1) {
        ++line_number;
        const auto fields = split_fields(line);
        if (fields.empty()) {
            continue;
        }

        const bool is_directory = fields.front() == "dir";
        const bool is_node = fields.front() == "nod";
        const size_t expected_fields = is_directory ? 5 : (is_node ? 8 : 0);
        if (expected_fields == 0 || fields.size() != expected_fields) {
            std::free(line);
            std::fclose(file);
            die("invalid device-node description '%s' at line %zu", filename.c_str(),
                line_number);
        }

        const std::string path(fields[1]);
        const unsigned long permissions =
                parse_unsigned(fields[2], 8, "mode", filename, line_number);
        (void)parse_unsigned(fields[3], 10, "uid", filename, line_number);
        (void)parse_unsigned(fields[4], 10, "gid", filename, line_number);
        if (permissions > 07777UL) {
            std::free(line);
            std::fclose(file);
            die("mode exceeds 07777 in '%s' at line %zu", filename.c_str(), line_number);
        }

        if (is_directory) {
            nodes.push_back(
                    {path, static_cast<mode_t>(S_IFDIR | permissions), static_cast<dev_t>(0)});
            continue;
        }

        if (fields[5] != "c" && fields[5] != "b") {
            std::free(line);
            std::fclose(file);
            die("invalid device type in '%s' at line %zu", filename.c_str(), line_number);
        }
        const unsigned long device_major =
                parse_unsigned(fields[6], 10, "major", filename, line_number);
        const unsigned long device_minor =
                parse_unsigned(fields[7], 10, "minor", filename, line_number);
        const mode_t type = fields[5] == "c" ? S_IFCHR : S_IFBLK;
        const dev_t device =
                static_cast<dev_t>(makedev(device_major, device_minor));
        if (major(device) != device_major || minor(device) != device_minor) {
            std::free(line);
            std::fclose(file);
            die("device number is not representable in '%s' at line %zu", filename.c_str(),
                line_number);
        }
        nodes.push_back({path, static_cast<mode_t>(type | permissions), device});
    }

    const int read_error = std::ferror(file) ? (errno == 0 ? EIO : errno) : 0;
    std::free(line);
    if (std::fclose(file) != 0 && read_error == 0) {
        die_errno("cannot close device-node description", filename);
    }
    if (read_error != 0) {
        errno = read_error;
        die_errno("cannot read device-node description", filename);
    }
    return nodes;
}

void emit_trailer() {
    pad_to(4);
    char header[111];
    const int length = std::snprintf(
            header, sizeof(header),
            "070701%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x%08x",
            next_inode++, 0U, 0U, 0U, 1U, 0U, 0U, 0U, 0U, 0U, 0U,
            static_cast<uint32_t>(sizeof(kTrailer)), 0U);
    if (length != 110) {
        die("failed to encode newc trailer");
    }
    write_stdout(header, static_cast<size_t>(length));
    write_stdout(kTrailer, sizeof(kTrailer));
    pad_to(256);
}

void usage(FILE* stream) {
    std::fprintf(stream,
                 "Usage: mkbootfs [-f FILE] [-n FILE] DIR[=PREFIX]...\n"
                 "\n"
                 "  -f, --file=FILE   Explicit canned mode configuration\n"
                 "  -n, --nodes=FILE  Device-node description file\n"
                 "  -h, --help        Print this help\n");
}

}  // namespace

int main(int argc, char** argv) {
    std::setvbuf(stdout, nullptr, _IOFBF, 1024 * 1024);

    std::vector<std::string> node_description_files;
    static const option long_options[] = {
            {"file", required_argument, nullptr, 'f'},
            {"help", no_argument, nullptr, 'h'},
            {"nodes", required_argument, nullptr, 'n'},
            {nullptr, 0, nullptr, 0},
    };

    int option_index = 0;
    int option_value;
    while ((option_value = getopt_long(argc, argv, "hf:n:", long_options, &option_index)) != -1) {
        switch (option_value) {
            case 'f':
                if (has_canned_config) {
                    die("only one canned configuration may be supplied");
                }
                read_canned_config(optarg);
                break;
            case 'n':
                node_description_files.emplace_back(optarg);
                break;
            case 'h':
                usage(stdout);
                return EXIT_SUCCESS;
            default:
                usage(stderr);
                return EXIT_FAILURE;
        }
    }

    if (optind == argc) {
        usage(stderr);
        die("no directories to process");
    }

    for (const auto& description : node_description_files) {
        for (const auto& node : read_device_nodes(description)) {
            emit_memory_entry(node.mode, node.device, node.path, nullptr, 0);
        }
    }

    for (int index = optind; index < argc; ++index) {
        std::string specification(argv[index]);
        const size_t separator = specification.find('=');
        const std::string input =
                separator == std::string::npos ? specification : specification.substr(0, separator);
        const std::string prefix =
                separator == std::string::npos ? std::string() : specification.substr(separator + 1);
        if (input.empty()) {
            die("empty input directory in '%s'", specification.c_str());
        }
        archive_directory(input, prefix);
    }

    emit_trailer();
    if (std::fflush(stdout) != 0) {
        die("failed flushing archive: %s", std::strerror(errno));
    }
    return EXIT_SUCCESS;
}
