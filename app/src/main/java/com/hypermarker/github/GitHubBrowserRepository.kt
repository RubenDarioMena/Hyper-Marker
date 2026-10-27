package com.hypermarker.github

import com.hypermarker.security.PathGuard
import org.json.JSONArray
import org.json.JSONObject

data class GitHubPage<T>(val items: List<T>, val nextPage: Int?, val capped: Boolean)

data class GitHubRepo(val id: Long, val owner: String, val name: String, val defaultBranch: String, val private: Boolean)
data class GitHubBranch(val name: String)
data class GitHubItem(val name: String, val path: String, val type: String)

/** Navegación acotada. La selección de una carpeta no amplía permisos del PAT. */
class GitHubBrowserRepository(private val api: GitHubApiClient, private val credential: () -> String?) {
    constructor(api: GitHubApiClient, auth: GitHubAuthStore) : this(api, auth::read)
    suspend fun repositories(page: Int = 1): List<GitHubRepo> = repositoryPage(page).items
    suspend fun repositoryPage(page: Int = 1): GitHubPage<GitHubRepo> {
        require(page in 1..5)
        val token = requireNotNull(credential()) { "Conecta GitHub antes de listar repositorios" }
        val response = api.get("/user/repos?per_page=100&page=$page&sort=updated", token, maxBytes = 2L * 1024 * 1024)
        return pageResult(parseRepos(JSONArray(response.body.toString(Charsets.UTF_8))), response, "/user/repos", page)
    }
    suspend fun repository(owner: String, repo: String): GitHubRepo {
        val json = api.get("/repos/${segment(owner)}/${segment(repo)}", credential(), maxBytes = 512_000).json()
        return parseRepo(json)
    }
    suspend fun branches(owner: String, repo: String, page: Int = 1): List<GitHubBranch> = branchPage(owner, repo, page).items
    suspend fun branchPage(owner: String, repo: String, page: Int = 1): GitHubPage<GitHubBranch> {
        require(page in 1..5)
        val response = api.get("/repos/${segment(owner)}/${segment(repo)}/branches?per_page=100&page=$page", credential(), maxBytes = 512_000)
        val array = JSONArray(response.body.toString(Charsets.UTF_8))
        return pageResult((0 until array.length()).map { GitHubBranch(array.getJSONObject(it).getString("name")) }, response, "/repos/${segment(owner)}/${segment(repo)}/branches", page)
    }
    suspend fun directory(owner: String, repo: String, branch: String, path: String): List<GitHubItem> {
        val base = "/repos/${segment(owner)}/${segment(repo)}/contents"
        val suffix = if (path.isBlank()) "" else "/" + PathGuard.normalize(path).split('/').joinToString("/") { segment(it) }
        val response = api.get("$base$suffix?ref=${branch.split('/').joinToString("%2F") { segment(it) }}", credential(), maxBytes = 512_000)
        val array = JSONArray(response.body.toString(Charsets.UTF_8))
        require(array.length() < 1000) { "La carpeta excede el listado de GitHub; elige una más pequeña" }
        return (0 until array.length()).map { i -> array.getJSONObject(i).let { GitHubItem(it.getString("name"), it.getString("path"), it.getString("type")) } }
    }
    private fun <T> pageResult(items: List<T>, response: GitHubResponse, expectedPath: String, page: Int): GitHubPage<T> {
        val link = response.header("Link")?.split(',')?.firstOrNull { it.contains("rel=\"next\"") }
        val next = link?.substringAfter('<')?.substringBefore('>')?.let { java.net.URI(it) }
        if (next != null) {
            require(next.scheme == "https" && next.host == "api.github.com" && next.userInfo == null && next.path == expectedPath) { "Paginación GitHub no válida" }
            val pageValue = next.rawQuery?.split('&')?.firstOrNull { it.startsWith("page=") }?.substringAfter('=')?.toIntOrNull()
            require(pageValue == page + 1) { "Paginación GitHub no consecutiva" }
        }
        return GitHubPage(items, if (next != null && page < 5) page + 1 else null, next != null && page == 5)
    }
    private fun parseRepos(array: JSONArray) = (0 until array.length()).map { parseRepo(array.getJSONObject(it)) }
    private fun parseRepo(value: JSONObject): GitHubRepo = GitHubRepo(value.getLong("id"),
        value.getJSONObject("owner").getString("login"), value.getString("name"),
        value.getString("default_branch"), value.optBoolean("private", false))
}

internal fun segment(value: String): String {
    require(value.isNotBlank() && value.length <= 255 && value != "." && value != "..")
    require(value.none { it == '/' || it == '\\' || it == '?' || it == '#' || it == '%' || it.isISOControl() })
    return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
}
