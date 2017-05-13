package com.iqmsoft.scalatra.knockout

import java.io.File

class StaticAssetController extends ScalatraSandbox {
  /*
    Special static handler for development, to force live update of
    CSS and JS files from the version-controlled directory
  */
  get("/") {
    contentType = "text/html"
    val webAppPath = servletContext.getResource("/").getPath
    new File(webAppPath + "../../src/main/webapp/index.html")
  }

  get("/css/*") {
    contentType = "text/css"
    serveFile("/css/")
  }

  get("/js/*") {
    serveFile("/js/")
  }

  private def serveFile(uriRoot: String): File = {
    val uriPath = uriRoot + params("splat")
    val webAppPath = servletContext.getResource("/").getPath
    new File(webAppPath + "../../src/main/webapp" + uriPath)
  }
}
