package csw.qa.logging;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class Log4j2Test {
  private static final Logger logger = LogManager.getLogger();

  public void foo() {
    logger.debug("This is a log4j deug message");
    logger.info("This is a log4j info message");
    logger.warn("This is a log4j warn message");
  }
}
