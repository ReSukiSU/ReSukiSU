package com.resukisu.resukisu.domain.model

data class CatalogAuthor(
    val name: String,
    val link: String,
)

data class ModuleReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val downloadCount: Int,
)

data class ModuleRelease(
    val name: String,
    val tagName: String,
    val publishedAt: String,
    val descriptionHTML: String,
    val assets: List<ModuleReleaseAsset>,
)

data class CatalogModule(
    val moduleId: String,
    val moduleName: String,
    val authors: String,
    val authorList: List<CatalogAuthor>,
    val summary: String,
    val metamodule: Boolean,
    val stargazerCount: Int,
    val updatedAt: String,
    val createdAt: String,
    val latestRelease: String,
    val latestReleaseTime: String,
    val latestVersionCode: Int,
    val latestAsset: ModuleRelease?,
    val installed: Boolean,
    val readme: String,
    val sourceUrl: String,
    val releases: List<ModuleRelease>,
)

sealed interface ModuleCatalogFailure {
    data object Offline : ModuleCatalogFailure
    data object NotFound : ModuleCatalogFailure
    data class Network(val message: String) : ModuleCatalogFailure
}

sealed interface ModuleCatalogResult<out T> {
    data class Success<T>(val value: T) : ModuleCatalogResult<T>
    data class Failure(val reason: ModuleCatalogFailure) : ModuleCatalogResult<Nothing>
}
