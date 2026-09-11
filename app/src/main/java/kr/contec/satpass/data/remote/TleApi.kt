package kr.contec.satpass.data.remote

import retrofit2.http.GET
import retrofit2.http.Url

/**
 * TLE 조회 API.
 *
 * 받아올 주소를 설정 화면에서 바꿀 수 있어야 하므로 고정 엔드포인트 대신
 * [Url] 로 전체 URL 을 그때그때 넘긴다. (Retrofit 의 baseUrl 은 무시된다.)
 *
 * 응답은 3줄 단위의 TLE 텍스트라서 스칼라(String) 컨버터로 그대로 받는다.
 */
interface TleApi {

    @GET
    suspend fun fetchTle(@Url url: String): String
}
