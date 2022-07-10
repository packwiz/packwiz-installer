package link.infra.packwiz.installer.util

import cc.ekblad.toml.TomlMapper
import cc.ekblad.toml.configuration.TomlMapperConfigurator
import cc.ekblad.toml.model.TomlValue

inline fun <reified T: Any> TomlMapperConfigurator.delegateTransitive(mapper: TomlMapper) {
	decoder { it: TomlValue -> mapper.decode<T>(it) }
	encoder { it: T -> mapper.encode(it) }
}