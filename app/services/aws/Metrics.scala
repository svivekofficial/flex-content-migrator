package services.aws

import java.util.concurrent.atomic.AtomicLong
import java.util.Date
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, Props}
import com.amazonaws.services.cloudwatch.model._
import play.api.libs.concurrent.Akka
import play.api.Play.current


object Metrics {

  val QuizzesMigratedInFlex = new CountMetric("quizzesMigratedInFlex")
  val QuizzesMigratedInR2 = new CountMetric("quizzesMigratedInR2")
  val QuizzesMigratedInQuizBuilder = new CountMetric("quizzesMigratedInQuizBuilder")

  val CrosswordsMigratedInFlex = new CountMetric("crosswordsMigratedInFlex")
  val CrosswordsMigratedInR2 = new CountMetric("crosswordsMigratedInR2")


  val all = List(
    QuizzesMigratedInR2,
    QuizzesMigratedInFlex,
    QuizzesMigratedInQuizBuilder,
    CrosswordsMigratedInFlex,
    CrosswordsMigratedInR2
  )

  private val reporter = new CloudWatchReporter(all)
  reporter.start

  def withCountIncr[R](metric : CountMetric)(fn : => R) = {
    val ret = fn
    metric.increment
    ret
  }

}


trait CloudWatchMetric {
  def flush(dimensions: Dimension*) :Seq[MetricDatum]
}


class CountMetric(metricName: String) extends CloudWatchMetric {
  val _count = new AtomicLong()

  def recordCount(c: Long) { _count.addAndGet(c) }
  def increment { _count.incrementAndGet }

  override def flush(dimensions: Dimension*) = Seq(
    new MetricDatum()
      .withMetricName(metricName)
      .withDimensions(dimensions: _*)
      .withValue(_count.getAndSet(0).toDouble)
      .withUnit(StandardUnit.Count)
      .withTimestamp(new Date())
  )
}

class CloudWatchReporter(metrics: Seq[CloudWatchMetric]) extends AwsInstanceTags {

  lazy val stageOpt = readTag("Stage")
  lazy val appOpt = readTag("App")

  val system = Akka.system

  def start {

    for (
      app <- appOpt;
      stage <- stageOpt
    ) {
      system.scheduler.scheduleOnce(
        delay = 1 minute,
        receiver = system.actorOf(Props(new CloudWatchReportActor(app, stage))),
        message = ReportMetrics
      )
    }
  }

  case object ReportMetrics

  class CloudWatchReportActor(app: String, stage: String) extends Actor {

    val appDimension = new Dimension().withName("App").withValue(app)
    val stageDimension = new Dimension().withName("Stage").withValue(stage)

    override def receive = {
      case ReportMetrics => {

        val data = metrics.flatMap(_.flush(appDimension, stageDimension))

        val metricData = new PutMetricDataRequest().withNamespace("AppMetrics").withMetricData(data: _*)
        AWS.CloudWatch.putMetricDataAsync(metricData)
        reschedule
      }
    }

    private def reschedule() {
      context.system.scheduler.scheduleOnce(1 minute, self, ReportMetrics)
    }

    override def postRestart(reason: Throwable) { reschedule }
  }
}
