package com.codahale.fig

import io.Source
import java.io.{File, InputStream}
import net.liftweb.json._
import net.liftweb.json.JsonAST._

/**
 * An exception class thrown when there is a configuration error.
 */
class ConfigurationException(message: String) extends Exception(message)

/**
 * A JSON-based configuration file. Line comments (i.e., //) are allowed.
 *
 * val config = new Configuration("config.json") // or an io.Source or an InputStream
 * config("rabbitmq.queue.name").as[String]
 *
 * @author coda
 */
class Configuration(src: Source) {
  case class Value(path: String, value: JsonAST.JValue) {
    /**
     * Returns the value as an instance of type A.
     */
    def as[A](implicit mf: Manifest[A]) = value.extract[A](DefaultFormats, mf)

    /**
     * Returns the value as an instance of type Option[A]. If the value exists,
     * Some(v: A) is returned; otherwise, None.
     */
    def asOption[A](implicit mf: Manifest[A]) = value.extractOpt[A](DefaultFormats, mf)

    /**
     * Returns the value as an instance of type A, or if the value does not
     * exist, the result of the provided function.
     */
    def or[A](default: => A)(implicit mf: Manifest[A]) = asOption[A](mf).getOrElse(default)

    /**
     * Returns the value as an instance of type A, or if it cannot be converted,
     * throws a ConfigurationException with an information error message.
     */
    def asRequired[A](implicit mf: Manifest[A]) = asOption[A] match {
      case Some(v) => v
      case None => throw new ConfigurationException(
        "%s property %s not found".format(mf.erasure.getSimpleName, path)
      )
    }

    /**
     * Returns the value as a instance of List[A], or if the value is not a JSON
     * array, an empty list.
     */
    def asList[A](implicit mf: Manifest[A]): List[A] = value match {
      case JField(_, JArray(list)) => list.map { _.extract[A](DefaultFormats, mf) }
      case other => List()
    }

    /**
     * Returns the value as an instance of Map[String, A], or if the value is
     * not a simple JSON object, an empty map.
     */
    def asMap[A](implicit mf: Manifest[A]): Map[String, A] = value match {
      case JField(_, o: JObject) =>
        if (mf.erasure == classOf[List[_]]) {
          o.obj.map { f =>
            val s = f.value match {
              case JArray(l) => l.map { _.extract(DefaultFormats, mf.typeArguments.head) }.toList
              case _ => Nil
            }
            f.name -> s.asInstanceOf[A]
          }.toMap
        } else {
          o.obj.map { f => f.name -> f.value.extract[A](DefaultFormats, mf) }.toMap
        }
      case other => Map()
    }
  }

  private val json = try { JsonParser.parse(src.mkString.replaceAll("""(^//.*|[\s]+//.*)""", "")) } finally { src.close }

  def this(filename: String) = this (Source.fromFile(new File(filename)))

  def this(stream: InputStream) = this (Source.fromInputStream(stream))

  /**
   * Given a dot-notation JSON path (e.g., "parent.child.fieldname"), returns
   * a Value which can be converted into a specific type or Option thereof.
   */
  def apply(path: String) = Value(path, path.split('.').foldLeft(json) { _ \ _ })
}
