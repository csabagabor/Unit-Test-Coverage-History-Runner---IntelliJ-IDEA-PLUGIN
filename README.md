[Support me on Patreon](https://www.patreon.com/csabagabor?fan_landing=true)

Available on Jetbrains Marketplace: https://plugins.jetbrains.com/plugin/15063-unit-test-coverage-history-runner  

> There are some other plugins which automatically re-run tests when a change is detected, but these plugins just use static code analysis to find
the tests which directly call the changed code. This does not work if you modifiy a private method which is only indirectly called in a test.
**This plugin works in this case as well because it saves which methods are called during the runtime of a test.**

# About 
This plugin lets you **click on a method in your project and automatically run all the unit tests which cover that method.**

# Manual (Help)
There are two steps to use the plugin:  

- 1. First you have to run as many unit tests as you can **with the new Unit Test Runner**(its icon has 1 red + 1 green triangle and a yellow arrow on top of the triangles).
 The more tests you run with it, the more coverage information you will have.  
 ![1](https://user-images.githubusercontent.com/37183688/93020032-9edebc00-f5e3-11ea-8b1e-856fa5c9676c.png)

- 2. To run all the unit tests which cover a particular method in your project, just right click on a method
and then choose -> **Run Unit Tests Which Cover This Method...(this is not shown for methods without coverage info)**
 Unit tests which cover that method will be automatically started.  
![2](https://user-images.githubusercontent.com/37183688/93020034-9f775280-f5e3-11ea-8721-5821a04b898a.png)


Also to **reset coverage information** (in case unexpected tests are run), you can go to Tools -> Reset Coverage Info...

# Limitations
When you encounter any test for which coverage information is not included, file an issue on GitHub.
For example the plugin doesn't work when using empty test methods with the Scenario annotation like <code>@Scenario("integration_tests")</code>

# Credits
IntelliJ IDEA Code Coverage Agent: https://github.com/JetBrains/intellij-coverage
