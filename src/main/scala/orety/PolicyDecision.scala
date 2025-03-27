package orety

import scala.concurrent.duration.FiniteDuration

enum PolicyDecision:
  case GiveUp
  case DelayAndRetry(delay: FiniteDuration)
