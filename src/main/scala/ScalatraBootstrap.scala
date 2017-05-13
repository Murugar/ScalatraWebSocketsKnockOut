import com.iqmsoft.scalatra.knockout.{WebsocketController, StaticAssetController}
import org.scalatra._
import javax.servlet.ServletContext

class ScalatraBootstrap extends LifeCycle {
  override def init(context: ServletContext) {
    context.mount(new StaticAssetController, "/")
    context.mount(new WebsocketController, "/ws")
    context.mount(new StaticAssetController, "/static/*")
  }
}
