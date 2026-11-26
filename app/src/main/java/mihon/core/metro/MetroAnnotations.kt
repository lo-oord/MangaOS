package mihon.core.metro

/**
 * The publisher's Metro qualifier annotations are compiled into `core-common` class files
 * (constructor parameters and classes) but the artifact that declares them is not published.
 * Declaring the same annotations keeps reflection/verification of those classes resolvable.
 * They are markers only; MangaOS wires its services explicitly through Injekt.
 */
annotation class AppCoroutineScope

annotation class IsDebugBuild
