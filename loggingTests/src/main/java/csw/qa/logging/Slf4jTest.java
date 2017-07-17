package csw.qa.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Slf4jTest {
  private Logger logger = LoggerFactory.getLogger(Slf4jTest.class);

  public void foo() {
    logger.debug("This is a slf4j debug message");
    logger.info("This is a slf4j info message");
    logger.warn("This is a slf4j warn message");
  }
}
