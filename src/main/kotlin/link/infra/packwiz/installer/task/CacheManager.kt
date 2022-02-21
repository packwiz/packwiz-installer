package link.infra.packwiz.installer.task

import kotlin.reflect.KProperty

class CacheManager {
	class CacheValue<T> {
		operator fun getValue(thisVal: Any?, property: KProperty<*>): T {
			TODO("Not yet implemented")
		}

		operator fun setValue(thisVal: Any?, property: KProperty<*>, value: T) {
			TODO("Not yet implemented")
		}

	}

	operator fun <T> get(cacheKey: CacheKey<T>): CacheValue<T> {
		return CacheValue()
	}


}