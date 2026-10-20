package com.hypermarker.security

import java.io.File

object PathGuard {
    fun normalize(path: String): String {
        require(path.isNotEmpty() && path.length <= 4096) { "Ruta vacía o demasiado larga" }
        require(!path.startsWith('/') && path.none { it == '\\' || it == '\u0000' || it == ':' || it == '%' }) { "Ruta no permitida: $path" }
        val parts = path.trimEnd('/').split('/')
        require(parts.size <= 64 && parts.all { it.isNotEmpty() && it != "." && it != ".." && it.length <= 255 }) { "Ruta insegura o demasiado profunda: $path" }
        return parts.joinToString("/")
    }
    fun resolve(root: File, path: String): File {
        val base = root.canonicalFile
        val target = File(base, normalize(path)).canonicalFile
        require(target.toPath().startsWith(base.toPath()) && target != base) { "Ruta fuera del proyecto" }
        return target
    }
}

object FolderRules {
    fun validateMove(id: String, parent: String?, parents: Map<String, String?>) {
        require(parent == null || parent in parents) { "La carpeta destino no existe" }
        val seen = mutableSetOf<String>()
        var current = parent
        while (current != null) {
            require(current != id && seen.add(current)) { "No puedes mover una carpeta dentro de sí misma o de sus descendientes" }
            current = parents[current]
        }
    }
}
