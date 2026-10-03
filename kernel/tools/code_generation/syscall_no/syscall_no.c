// SPDX-License-Identifier: GPL-2.0

#include <ctype.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static void trim_left(char **line)
{
    while (isspace((unsigned char)**line))
        (*line)++;
}

static void emit_line(FILE *output, const char *line)
{
    fputs(line, output);
    fputc('\n', output);
}

static void emit_define(FILE *output, char *line, const char *prefix)
{
    char name[256];
    char value[512];
    char *cursor = line;
    int fields;

    trim_left(&cursor);
    if (strncmp(cursor, "#define", 7) || !isspace((unsigned char)cursor[7]))
        return;
    cursor += 7;
    trim_left(&cursor);
    if (strncmp(cursor, "__NR_", 5))
        return;
    fields = sscanf(cursor, "%255s %511[^\n]", name, value);
    if (fields != 2 || strchr(value, '\\'))
        return;
    fprintf(output, "#define %s%s %s\n", prefix, name + 5, value);
}

static int parse_file(FILE *output, const char *path, const char *prefix)
{
    FILE *file;
    char line[4096];

    file = fopen(path, "r");
    if (!file) {
        fprintf(stderr, "syscall_no: cannot open %s: %s\n", path, strerror(errno));
        return -1;
    }
    while (fgets(line, sizeof(line), file))
        emit_define(output, line, prefix);
    fclose(file);
    return 0;
}

int main(int argc, char **argv)
{
    FILE *output;
    int compat = 0;
    int arm64 = 0;
    int i;
    const char *unistd32 = NULL;

    if (argc < 2 || argc > 4) {
        fprintf(stderr, "syscall_no: usage: %s <out_file> [--arm64] [--arm64-compat] [unistd32.h]\n", argv[0]);
        return EXIT_FAILURE;
    }

    for (i = 2; i < argc; i++) {
        if (!strcmp(argv[i], "--arm64-compat")) {
            arm64 = 1;
            compat = 1;
        } else if (!strcmp(argv[i], "--arm64")) {
            arm64 = 1;
        } else {
            unistd32 = argv[i];
        }
    }
    if (unistd32 && !compat) {
        fprintf(stderr, "syscall_no: unistd32.h requires --arm64-compat\n");
        return EXIT_FAILURE;
    }

    output = fopen(argv[1], "w");
    if (!output) {
        fprintf(stderr, "syscall_no: cannot open %s: %s\n", argv[1], strerror(errno));
        return EXIT_FAILURE;
    }

    emit_line(output, "/** Auto generated file. DO NOT EDIT **/");
    emit_line(output, "");
    emit_line(output, "#ifndef KSU_NR_COMPAT_H");
    emit_line(output, "#define KSU_NR_COMPAT_H");
    emit_line(output, "");
    emit_line(output, "#include <asm/unistd.h>");
    emit_line(output, "");
    if (compat) {
        emit_line(output, "#include <asm/syscall.h>");
        if (!unistd32) {
            emit_line(output, "#include <asm/unistd_compat_32.h>");
            emit_line(output, "");
        } else {
            fprintf(output, "/** Generated from %s **/\n", unistd32);
            if (parse_file(output, unistd32, "__NR_compat32_") < 0) {
                fclose(output);
                return EXIT_FAILURE;
            }
            emit_line(output, "");
        }
        emit_line(output, "#define ksu_get_syscall_no(name) (is_compat_task() ? __NR_compat32_##name : __NR_##name)");
        emit_line(output, "#define ksu_get_compat_syscall_no(name) __NR_compat32_##name");
    } else {
        emit_line(output, "#define ksu_get_syscall_no(name) __NR_##name");
        emit_line(output, "#define ksu_get_compat_syscall_no(name) \\");
        emit_line(output, arm64 ?
                              "({ BUILD_BUG_ON_MSG(1, \"ksu_get_compat_syscall_no() requires CONFIG_COMPAT\"); 0; })" :
                              "({ BUILD_BUG_ON_MSG(1, \"ksu_get_compat_syscall_no() requires arm64\"); 0; })");
    }
    emit_line(output, "");
    emit_line(output, "#endif");
    if (fclose(output) != 0) {
        fprintf(stderr, "syscall_no: cannot close %s: %s\n", argv[1], strerror(errno));
        return EXIT_FAILURE;
    }
    return EXIT_SUCCESS;
}
