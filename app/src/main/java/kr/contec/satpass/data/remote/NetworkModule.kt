package kr.contec.satpass.data.remote

import kr.contec.satpass.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit / OkHttp 구성.
 *
 * 실제 요청 주소는 [TleApi.fetchTle] 의 `@Url` 로 넘기므로 여기의 baseUrl 은
 * Retrofit 이 요구하는 형식을 맞추기 위한 자리값일 뿐이다.
 */
object NetworkModule {

    private const val PLACEHOLDER_BASE_URL = "https://celestrak.org/"

    fun createOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        // 활성 위성 전체 TLE 는 응답이 커서 읽기 타임아웃을 넉넉하게 준다.
        .readTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(
                    HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
                )
            }
        }
        .build()

    fun createTleApi(client: OkHttpClient): TleApi = Retrofit.Builder()
        .baseUrl(PLACEHOLDER_BASE_URL)
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(TleApi::class.java)
}
