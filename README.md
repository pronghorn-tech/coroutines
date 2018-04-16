# Pronghorn Coroutine Framework
The Pronghorn Coroutine Framework is [Kotlin](https://kotlinlang.org/) framework for development of high performance, IO-driven applications. It is opinionated about its threading model, and currently does not support blocking operations.

_Note: Pronghorn is still considered early in development.  As such, documentation is minimal and APIs may change.  It will continue being developed as it is being used by a currently private project, so if you are interested it is recommended to follow the project for updates.  Documentation will be forthcoming when the API stabilizes._   

## Use Cases
This framework is best suited for IO-driven applications where high performance is critical. For instance, [a web server](https://github.com/pronghorn-tech/server).

## Usage
Pronghorn is available via [Maven Central](http://search.maven.org/#artifactdetails%7Ctech.pronghorn%7Ccoroutines%7C0.1.3%7Cjar).  The current version is `0.1.3`.

# Overview
Applications built with the framework consist of one or more services each running on a set of workers, typically one worker per thread. Shared data is minimized between workers allowing each worker's services to function without synchronization mechanisms.

## Getting Started
The following is a simple Hello World example of a Pronghorn Coroutine Framework application.

```kotlin    
class ExampleApplication(override val workerCount: Int): CoroutineApplication<ExampleWorker>() {
    override fun spawnWorker(): ExampleWorker = ExampleWorker()
}
```

Let's start by defining an ExampleApplication class that extends the CoroutineApplication abstract class. This generic class takes a type parameter declaring what type of worker this application will use. In addition, the number of workers to spawn, and a method that produces new workers must be defined.

```kotlin
class ExampleWorker : CoroutineWorker(){
    override val services = listOf(ExampleService(this))
}
```

Next, an ExampleWorker class worker extends the CoroutineWorker abstract class, and at a minimum must define a list of services to run.

```kotlin
class ExampleService(override val worker: CoroutineWorker) : IntervalService(Duration.ofSeconds(1)){
    override fun process() {
        println("${Date().toInstant()} : Hello World from worker ${worker.workerID}")
    }
}
```

This ExampleService is an IntervalService, one of several available service types. This one runs its process() function on the requested interval.

```kotlin
fun main(args: Array<String>) {
    val workerCount = Runtime.getRuntime().availableProcessors()
    val app = ExampleApplication(workerCount)
    app.start()
}
```

Finally, we'll instantiate the application, and start it. Since the workerCount here is defined by the number of available processors, on a dual core machine this would produce output similar to :

```
2017-09-14T02:41:47.734Z : Hello World from worker 1
2017-09-14T02:41:47.734Z : Hello World from worker 2
...
2017-09-14T02:41:48.734Z : Hello World from worker 2
2017-09-14T02:41:48.734Z : Hello World from worker 1
...
2017-09-14T02:41:49.734Z : Hello World from worker 2
2017-09-14T02:41:49.734Z : Hello World from worker 1
```

#Releases

_This section will be populated in the future._

#ServiceTypes

_This section will be populated in the future._

# License
Copyright 2017 Pronghorn Technology LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
