package link.infra.packwiz.installer.target

import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.relativeTo

data class CachedTargetStatus(val target: CachedTarget, var isValid: Boolean, var markDisabled: Boolean)

fun validate(targets: List<CachedTarget>, baseDir: Path) = runCatching {
	val results = targets.map {
		CachedTargetStatus(it, isValid = false, markDisabled = false)
	}
	val tree = buildTree(results, baseDir)

	// Efficient file exists checking using directory listing, several orders of magnitude faster than Files.exists calls
	Files.walkFileTree(baseDir, setOf(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE, object : FileVisitor<Path> {
		var currentNode: PathNode<CachedTargetStatus> = tree

		override fun preVisitDirectory(dir: Path?, attrs: BasicFileAttributes?): FileVisitResult {
			if (dir == null) {
				return FileVisitResult.SKIP_SUBTREE
			}
			val subdirNode = currentNode.subdirs[dir.getName(dir.nameCount - 1)]
			return if (subdirNode != null) {
				currentNode = subdirNode
				FileVisitResult.CONTINUE
			} else {
				FileVisitResult.SKIP_SUBTREE
			}
		}

		override fun visitFile(file: Path?, attrs: BasicFileAttributes?): FileVisitResult {
			if (file == null) {
				return FileVisitResult.CONTINUE
			}
			// TODO: these are relative paths to baseDir
			// TODO: strip the .disabled for lookup
			val target = currentNode.files[file.getName(file.nameCount - 1)]
			if (target != null) {
				val disabledFile = file.endsWith(".disabled")
				// If a .disabled file and the actual file both exist, mark as invalid if the target is disabled
				if ((disabledFile )) {

				}
			}
			return FileVisitResult.CONTINUE
		}

		@Throws(IOException::class)
		override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
			if (exc != null) {
				throw exc
			}
			throw IOException("visitFileFailed called with no exception")
		}

		@Throws(IOException::class)
		override fun postVisitDirectory(dir: Path?, exc: IOException?): FileVisitResult {
			if (exc != null) {
				throw exc
			} else {
				val parent = currentNode.parent
				if (parent != null) {
					currentNode = parent
				} else {
					throw IOException("Invalid visitor tree structure")
				}
				return FileVisitResult.CONTINUE
			}
		}
	})

	results
}

fun buildTree(targets: List<CachedTargetStatus>, baseDir: Path): PathNode<CachedTargetStatus> {
	val root = PathNode<CachedTargetStatus>()
	for (target in targets) {
		val relPath = target.target.cachedLocation.relativeTo(baseDir)
		var node = root
		// Traverse all the directory components, except for the last one
		for (i in 0 until (relPath.nameCount - 1)) {
			node = node.createSubdir(relPath.getName(i))
		}
		node.files[relPath.getName(relPath.nameCount - 1)] = target
	}
	return root
}

data class PathNode<T>(val subdirs: MutableMap<Path, PathNode<T>>, val files: MutableMap<Path, T>, val parent: PathNode<T>?) {
	constructor() : this(mutableMapOf(), mutableMapOf(), null)

	fun createSubdir(nextComponent: Path) = subdirs.getOrPut(nextComponent, { PathNode(mutableMapOf(), mutableMapOf(), this) })
}