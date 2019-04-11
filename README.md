# Scala effectful AWS client
NOTE:
Although this repo is public, we are still treating it as a private repo in terms of governance: there are no stability guarantees, and the changes are entirely driven by the needs of our team.

The medium term plan is that several actual open source projects will be spawn out of comms-aws into separate libraries.
We don't plan to make this a catch-all bucket to wrap all of the AWS SDK, but to use it as a testbed to produce high-quality, purely functional libraries for some of the AWS services.

The libraries that we plan to support in the near to medium future are:

- A purely functional, non blocking, fs2-based DynamoDb client.
- An interface for AWS signing (unclear for now if it will be native or wrap the Java client)

The libraries that we might support, or might stay as semi-private in comms-aws are:
- An S3 client

There are no further plans at the moment.
