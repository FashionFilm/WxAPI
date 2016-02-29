package controllers

import java.security.MessageDigest
import javax.inject.Inject

import core.CoreApi
import play.api.cache.CacheApi
import play.api.mvc._
import play.api.{Logger, Play}
import play.cache.NamedCache

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.xml.{NodeSeq, PCData}

class Application @Inject()(@NamedCache("redis-cache") redisCache: CacheApi) extends Controller {

  Logger.debug("Controller Application initialized")

  private val coreApi = Play.current.injector instanceOf classOf[CoreApi]

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  /**
    * 验证服务器地址的有效性
    * @return
    */
  def serverValidation = Action.async(request => {
    // 获得token
    val token = (Play.current.configuration getString "token").get
    val queryStrings = (request.queryString mapValues (_ mkString "")) + ("token" -> token)
    val echo = queryStrings("echostr")

    val signatureInvolvedKeys = Seq("token", "timestamp", "nonce")
    val stringToSign = (signatureInvolvedKeys map queryStrings.apply).sorted mkString ""

    val md = MessageDigest.getInstance("SHA-1")
    val bytes = md.digest(stringToSign.getBytes)
    val signature = bytes map ("%02x" format _) mkString ""

    Future.successful(
      if (queryStrings("signature") == signature) {
        Logger.info(s"Validation passed. echo: $echo")
        Results.Ok(echo)
      } else {
        Logger.info("Validation failed")
        Results.Unauthorized
      }
    )
  })

  def accessToken() = Action.async {
    val token = coreApi.accessToken()
    token map (v => Results.Ok(v getOrElse ""))
  }

  def wrapXmlResult(result: NodeSeq): Result = {
    Results.Ok(result) //withHeaders ("Content-Type" -> "text/xml; charset=utf-8")
  }

  def receivedTextMessage(data: NodeSeq): Future[NodeSeq] = {
    val fromUser = PCData((data \ "FromUserName").text)
    val toUser = PCData((data \ "ToUserName").text)
    val contents = (data \ "Content").text
    val reply = PCData(s"你刚刚说：$contents")

    val result =
      <xml>
        <ToUserName>
          {fromUser}
        </ToUserName>
        <FromUserName>
          {toUser}
        </FromUserName>
        <CreateTime>
          {System.currentTimeMillis() / 1000}
        </CreateTime>
        <MsgType>
          {PCData("text")}
        </MsgType>
        <Content>
          {reply}
        </Content>
      </xml>

    Future.successful(result)
  }

  def entry() = Action.async(BodyParsers.parse.raw)(
    request => {
      val raw = request.body.asBytes() getOrElse new Array(0)
      val requestXml = scala.xml.XML.loadString(new String(raw, "utf-8"))

      requestXml match {
        case nodeSeq: NodeSeq =>
          // 消息种类
          val msgType = (nodeSeq \ "MsgType").text
          msgType match {
            case "text" =>
              val r = receivedTextMessage(requestXml) map wrapXmlResult
              r map (v => {
                v
              })
            case _ =>
              Future.successful(Results.Ok("success"))
          }
        case _ =>
          Future.successful(Results.Ok("success"))
      }
    }
  )
}
