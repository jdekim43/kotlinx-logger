plugins {
    id("convention.kotlin-multiplatform")
    id("convention.publish")
}

kotlin {
    applyDefaultHierarchyTemplate()

    sourceSets {
//        all {
//            languageSettings.optIn("kotlin.RequiresOptIn")
//            languageSettings.optIn("kotlin.contracts.ExperimentalContracts")
//        }

        commonMain.dependencies {
            implementation(libs.kotlinx)
            implementation(libs.stately.collections)

            implementation(kt.kotlinx.coroutine)
        }

        val jvmAndAndroidMain = create("jvmAndAndroidMain") {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmAndAndroidMain)
        named("androidMain") { dependsOn(jvmAndAndroidMain) }

        nativeMain.dependencies {
            api(kt.kotlinx.coroutine)
        }

        webMain.dependencies {
            api(kt.kotlinx.coroutine)
        }
    }
}
