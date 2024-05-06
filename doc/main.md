# Java在线IDE

这个项目是基于 SpringBoot 实现的在线 Java IDE，能够远程运行客户端发送的 Java 代码的 main 方法，并将程序的标准输出内容以及运行时异常信息反馈给客户端。此外，还给出了一种 CI/CD 的具体实现。

项目中涉及到许多 Java 基础知识，例如 Java 程序的编译和运行过程、Java 类加载机制、Java 类文件结构和 Java 反射等。此外，还包括了一个简单的并发问题：如何将一个非线程安全的类转变为线程安全的类。

#### 涉及知识点

- Java动态编译
- Java类加载机制
- Java反射
- CI/CD

## 项目实现

当我们拿到一个新的Web项目时，我们面临的第一个挑战通常是理解其代码结构。传统的方法可能是按顺序查看每个文件夹和文件，但这种方法可能不是最高效的。相反，一个更有成效的策略是按照项目的数据流传递来审查代码。

在Web项目中，数据流从客户端开始，通过网络发送到服务器的后端。因此，一个合理的起点是后端的控制层，这是数据进入后端的入口点。控制层负责处理来自前端的请求，并调用适当的服务和数据模型来响应这些请求。通过优秀查看控制层，我们可以快速地了解后端提供了哪些接口给前端使用，以及这些接口是如何处理数据的。

> com/example/online_java_ide/controller/RunCodeController.java

控制层很简洁，只包含一个 Java 文件。其中有两个函数，其中重点在于 `runCode` 函数，从函数名就能看出它的作用是运行代码。在这个函数中，关键语句是：

```java
String runResult = executeStringService.executeString(source, systemIn);
```

通过调用 `executeStringService` 的 `executeString` 方法来获取代码执行后的结果。因此，我们需要查找 `executeString` 方法的具体实现。

> com/example/online_java_ide/service/impl/ExecuteStringServiceImpl.java

`executeString` 方法定义在服务层，处理流程可以总结如下：

1. 创建诊断信息收集器以捕获编译过程中的诊断信息。
2. 使用 `StringCompiler.compile(code, diagnostics)` 方法编译传入的代码，获取编译后的字节码。
3. 如果编译成功，则创建一个 `Callable` 匿名内部类，实现 `call()` 方法来执行编译后的字节码。
4. 提交 `Callable` 对象给 `executorService` 进行执行，并获得一个 `Future` 对象。
5. 尝试从 `Future` 对象中获取执行结果：
    - 如果在规定时间内执行完成，则返回执行结果。
    - 如果超时，则返回超时提示。
    - 如果执行中发生异常，则返回相应的异常信息。
6. 无论执行是否完成，最终都会取消 `Future` 的执行，并根据执行结果返回相应的结果字符串。

#### 诊断信息收集器