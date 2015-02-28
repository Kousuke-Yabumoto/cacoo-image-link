package views.helper

/**
 * Created by z00036 on 2015/02/28.
 */
object TextHelper {
  implicit class TextViewHelper(val text: String) {
    def cut(length: Int) = if (text.length <= 15) text else s"${text.substring(0, 15)}..."
  }
}
