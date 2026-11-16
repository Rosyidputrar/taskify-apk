plugins {
    alias(libs.plugins.taskify.android.library)
    alias(libs.plugins.taskify.android.compose)
}

android {
    namespace = "com.rosyidputrar.taskify.core.designsystem"
}

dependencies {
    api(libs.compose.ui)
    api(libs.compose.ui.graphics)
    api(libs.compose.foundation)
    api(libs.compose.animation)
    api(libs.compose.material3)
    api(libs.compose.material.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.zxing.core)
    api(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
