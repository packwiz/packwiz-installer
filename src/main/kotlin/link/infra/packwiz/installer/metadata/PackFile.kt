package link.infra.packwiz.installer.metadata

import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import link.infra.packwiz.installer.metadata.hash.HashFormat
import link.infra.packwiz.installer.target.path.PackwizPath
import link.infra.packwiz.installer.util.delegateTransitive

data class PackFile(
	val name: String,
	val packFormat: PackFormat = PackFormat.DEFAULT,
	val index: IndexFileLoc,
	val versions: Map<String, String> = mapOf()
) {
	data class IndexFileLoc(
		val file: PackwizPath<*>,
		val hashFormat: HashFormat<*>,
		val hash: String,
	) {
		companion object {
			fun mapper(base: PackwizPath<*>) = tomlMapper {
				mapping<IndexFileLoc>("hash-format" to "hashFormat")
				delegateTransitive<PackwizPath<*>>(PackwizPath.mapperRelativeTo(base))
				delegateTransitive<HashFormat<*>>(HashFormat.mapper())
			}
		}
	}

	companion object {
		fun mapper(base: PackwizPath<*>) = tomlMapper {
			mapping<PackFile>("pack-format" to "packFormat")
			decoder { it: TomlValue.String -> PackFormat(it.value) }
			encoder { it: PackFormat -> TomlValue.String(it.format) }
			delegateTransitive<IndexFileLoc>(IndexFileLoc.mapper(base))
		}
	}
}