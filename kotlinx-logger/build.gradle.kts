plugins {
    id("convention.kotlin-multiplatform")
    id("convention.publish")
}

kotlin {
    sourceSets {
//        all {
//            languageSettings.optIn("kotlin.RequiresOptIn")
//            languageSettings.optIn("kotlin.contracts.ExperimentalContracts")
//        }

        commonMain.dependencies {
            implementation(libs.kotlinx)
            implementation(libs.stately.collections)

            compileOnly(kt.kotlinx.coroutine)
        }

        nativeMain.dependencies {
            api(kt.kotlinx.coroutine)
        }

        webMain.dependencies {
            api(kt.kotlinx.coroutine)
        }
    }
}
