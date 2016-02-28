package controllers

import java.security.MessageDigest
import javax.inject.Inject

import com.fasterxml.jackson.databind.ObjectMapper
import core.CoreApi
import play.api.cache.CacheApi
import play.api.mvc._
import play.api.{ Logger, Play }
import play.cache.NamedCache

import scala.concurrent.ExecutionContext.Implicits.global

class Application @Inject() (@NamedCache("redis-cache") redisCache: CacheApi) extends Controller {

  Logger.debug("Controller Application initialized")

  private val coreApi = Play.current.injector instanceOf classOf[CoreApi]

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  /**
   * 验证服务器地址的有效性
   * @return
   */
  def serverValidation = Action(request => {
    // 获得token
    val token = (Play.current.configuration getString "token").get
    val queryStrings = (request.queryString mapValues (_ mkString "")) + ("token" -> token)
    val echo = queryStrings("echostr")

    val signatureInvolvedKeys = Seq("token", "timestamp", "nonce")
    val stringToSign = (signatureInvolvedKeys map queryStrings.apply).sorted mkString ""

    val md = MessageDigest.getInstance("SHA-1")
    val bytes = md.digest(stringToSign.getBytes)
    val signature = bytes map ("%02x" format _) mkString ""

    if (queryStrings("signature") == signature) {
      Logger.info(s"Validation passed. echo: $echo")
      Results.Ok(echo)
    } else {
      Logger.info("Validation failed")
      Results.Unauthorized
    }
  })

  def accessToken() = Action.async {
    val mapper = new ObjectMapper()
    val token = coreApi.accessToken()
    token map (v => Results.Ok(v getOrElse ""))
  }
}
