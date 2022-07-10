package link.infra.packwiz.installer.metadata

import cc.ekblad.toml.delegate
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import link.infra.packwiz.installer.metadata.curseforge.UpdateData
import link.infra.packwiz.installer.metadata.hash.Hash
import link.infra.packwiz.installer.metadata.hash.HashFormat
import link.infra.packwiz.installer.target.ClientHolder
import link.infra.packwiz.installer.target.Side
import link.infra.packwiz.installer.target.path.HttpUrlPath
import link.infra.packwiz.installer.target.path.PackwizPath
import link.infra.packwiz.installer.util.delegateTransitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.Source
import kotlin.reflect.KType

data class ModFile(
	val name: String,
	val filename: PackwizPath<*>,
	val side: Side = Side.BOTH,
	val download: Download,
	val update: Map<String, UpdateData> = mapOf(),
	val option: Option = Option(false)
) {
	data class Download(
		val url: PackwizPath<*>?,
		val hashFormat: HashFormat<*>,
		val hash: String,
		val mode: DownloadMode = DownloadMode.URL
	) {
		companion object {
			fun mapper() = tomlMapper {
				decoder<TomlValue.String, PackwizPath<*>> { it -> HttpUrlPath(it.value.toHttpUrl()) }
				mapping<Download>("hash-format" to "hashFormat")

				delegateTransitive<HashFormat<*>>(HashFormat.mapper())
				delegate<DownloadMode>(DownloadMode.mapper())
			}
		}
	}

	@Transient
	val resolvedUpdateData = mutableMapOf<String, PackwizPath<*>>()

	data class Option(
		val optional: Boolean,
		val description: String = "",
		val defaultValue: Boolean = false
	) {
		companion object {
			fun mapper() = tomlMapper {
				mapping<Option>("default" to "defaultValue")
			}
		}
	}

	@Throws(Exception::class)
	fun getSource(clientHolder: ClientHolder): Source {
		return when (download.mode) {
			DownloadMode.URL -> {
				(download.url ?: throw Exception("No download URL provided")).source(clientHolder)
			}
			DownloadMode.CURSEFORGE -> {
				if (!resolvedUpdateData.contains("curseforge")) {
					throw Exception("Metadata file specifies CurseForge mode, but is missing metadata")
				}
				return resolvedUpdateData["curseforge"]!!.source(clientHolder)
			}
		}
	}

	@get:Throws(Exception::class)
	val hash: Hash<*>
		get() = download.hashFormat.fromString(download.hash)

	companion object {
		fun mapper(base: PackwizPath<*>) = tomlMapper {
			delegateTransitive<PackwizPath<*>>(PackwizPath.mapperRelativeTo(base))

			delegateTransitive<Option>(Option.mapper())
			delegateTransitive<Download>(Download.mapper())

			delegateTransitive<Side>(Side.mapper())

			val updateDataMapper = UpdateData.mapper()
			decoder { type: KType, it: TomlValue.Map ->
				if (type.arguments[1].type?.classifier == UpdateData::class) {
					updateDataMapper.decode<Map<String, UpdateData>>(it)
				} else {
					pass()
				}
			}
		}
	}
}