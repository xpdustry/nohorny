A few things when working with this codebase:

- You SHOULD NOT implement tests unless explicitly asked. You SHOULD use temporary tests to validate your code instead.
- You SHOULD avoid doing defensive programming for generic internal code (null checks, assertions, etc...)
  unless you are working with the public api or serialization.
- You SHOULD work with a YOLO attitude, not concerning yourself with compatibility or regressions.
  That means, you CAN modify any file necessary to complete a task, allowing you to avoid workarounds and inneficient solutions.
