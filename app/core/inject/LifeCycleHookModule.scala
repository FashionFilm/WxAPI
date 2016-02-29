package core.inject

import javax.inject.{ Inject, Singleton }

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.inject.AbstractModule
import core.CoreApi
import play.api.libs.ws.WSClient
import play.api.{ Configuration, Environment, Logger }

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

/**
 * Created by zephyre on 2/29/16.
 */
class LifeCycleHookModule extends AbstractModule {
  override def configure() = {
    (bind(classOf[LifeCycleHook]) to classOf[LifeCycleHookImpl]).asEagerSingleton()
  }
}

trait LifeCycleHook

@Singleton
class LifeCycleHookImpl @Inject() (
    ws: WSClient,
    coreApi: CoreApi,
    environment: Environment
) extends LifeCycleHook {
  {
    // onStart
    Logger.info("LifeCycleHook started")
    createMenu()
  }

  /**
   *
   */
  def createMenu(): Unit = {
    Logger.info("Creating menu...")
    // 定义菜单
    val mapper = new ObjectMapper()
    val homeButton = mapper.createObjectNode() put ("type", "view") put ("name", "时尚搜索") put
      ("url", "http://fashionfilm.quantumex.cn/")
    val menu = mapper.createObjectNode() set ("button", mapper.createArrayNode() add homeButton)

    val future = (for {
      accessToken <- coreApi.accessToken()
      response <- {
        val configuration = Configuration.load(environment)
        val baseURL = (configuration getString "wechatApiURL").get
        val apiURL = s"$baseURL/cgi-bin/menu/create?access_token=${accessToken.get}"
        ws url apiURL withHeaders ("ContentType" -> "application/json; charset=utf-8") post {
          mapper writeValueAsString menu
        }
      }
    } yield {
      val status = response.status
      val body = response.body
      Logger.debug(s"Menu created: status=$status, response=$body")
      ()
    }) recover {
      case e: Exception =>
        Logger.error(s"Failed to create menu: ${e.getMessage}")
        System.exit(-1)
    }
    Await.ready(future, Duration.Inf)
  }
}
