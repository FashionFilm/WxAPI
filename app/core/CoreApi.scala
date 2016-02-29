package core

import javax.inject.Inject

import com.fasterxml.jackson.databind.ObjectMapper
import play.api.cache.CacheApi
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Environment, Logger }
import play.cache.NamedCache

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

/**
 * Created by zephyre on 2/29/16.
 */
class CoreApi @Inject() (
    @NamedCache("redis-cache") redisCache: CacheApi,
    ws: WSClient,
    environment: Environment
) {

  Logger.debug("CoreApi initialized")

  case class AccessToken(token: String, expiresIn: Int)

  /**
   * 获得access token
   * @return
   */
  def accessToken() = {
    for {
      cachedToken <- Future(redisCache.get[String]("fashionFilm/accessToken")) // 尝试从cache中读取
      wxToken <- cachedToken map (Option.apply[String] _ andThen Future.successful _) getOrElse {
        Logger.debug("Cached missed. Trying to generat a new token.")

        // 从cache中没有获得access token, 尝试从微信服务器获得
        val configuration = Configuration.load(environment)
        val appid = (configuration getString "appid").get
        val secret = (configuration getString "secret").get
        val url = s"https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=$appid&secret=$secret"

        ws.url(url).get map (response => {
          val mapper = new ObjectMapper()
          val ret = Try({
            val node = mapper readTree response.body
            val token = Option(node get "access_token") map (_.asText()) getOrElse ""
            val expiresIn = Option(node get "expires_in") map (_.asInt(0)) getOrElse 0

            if (token.isEmpty && expiresIn == 0) {
              // 发生了错误
              Logger.warn(s"Something wrong in fetching the access token: ${response.body}")
              throw new RuntimeException()
            } else {
              AccessToken(token, expiresIn)
            }
          }).toOption

          ret map (v => {
            import scala.concurrent.duration._
            import scala.language.postfixOps

            // 比真实的过期时间提前一分钟
            val tmp = v.expiresIn - 60
            val expiresIn = if (tmp <= 0) 0 else tmp
            Logger.debug(s"New token generated: ${v.token}, expires in $expiresIn seconds")
            redisCache.set("fashionFilm/accessToken", v.token, expiresIn seconds)

            Option(v.token)
          }) getOrElse None
        })
      }
    } yield {
      wxToken
    }
  }

}
