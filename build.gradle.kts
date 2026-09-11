// 최상위 빌드 스크립트. 하위 모듈 공통 설정을 여기에 둔다.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
