package controllers

import play.api._
import play.api.mvc._
import scala.concurrent.ExecutionContext.Implicits.global

import models.Image

object Images extends Controller {
  
  def selectAll(userName: String) = Action.async { implicit request => 
    Image.list(userName) map { images => 
      Ok(views.html.images(images))
    }
  }
  
  def preview(user: String, diagramId: String, sheetId: String) = Action.async { implicit request => 
    Image.previewImage(user, diagramId, sheetId) map { image => 
      Ok (image).as("image/png")
    }
  }
  
  def show(user: String, diagramId: String, sheetId: String) = Action.async { implicit request => 
    Image.getImage(user, diagramId, sheetId) map { image => 
      Ok (image).as("image/png")
    }
  }
  
  def update(user: String, diagramId: String, sheetId: String) = Action.async { implicit request => 
    Image.updateImage(user, diagramId, sheetId) map { image => 
      Ok (image).as("image/png")
    }
  }
}