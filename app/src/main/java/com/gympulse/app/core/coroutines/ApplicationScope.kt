package com.gympulse.app.core.coroutines

import javax.inject.Qualifier

/** Scope that lives as long as the application process; used for work that must outlive screens. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
