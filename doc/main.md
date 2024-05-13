# Java在线IDE

这个项目是基于 SpringBoot 实现的在线 Java IDE，能够远程运行客户端发送的 Java 代码的 main 方法，并将程序的标准输出内容以及运行时异常信息反馈给客户端。此外，还给出了一种 CI/CD 的具体实现。

项目中涉及到许多 Java 基础知识，例如 Java 程序的编译和运行过程、Java 类加载机制、Java 类文件结构和 Java 反射等。此外，还包括了一个简单的并发问题：如何将一个非线程安全的类转变为线程安全的类。

#### 涉及知识点

- Java动态编译
- Java类加载机制
- Java反射
- CI/CD

## 项目实现

当拿到一个新的Web项目时，面临的第一个挑战通常是理解其代码结构。传统的方法可能是按顺序查看每个文件夹和文件，但这种方法可能不是最高效的。相反，一个更有成效的策略是按照项目的数据流传递来审查代码。

在Web项目中，数据流从客户端开始，通过网络发送到服务器的后端。因此，一个合理的起点是后端的控制层，这是数据进入后端的入口点。控制层负责处理来自前端的请求，并调用适当的服务和数据模型来响应这些请求。通过优秀查看控制层，可以快速地了解后端提供了哪些接口给前端使用，以及这些接口是如何处理数据的。

> com/example/online_java_ide/controller/RunCodeController.java

控制层很简洁，只包含一个 Java 文件。其中有两个函数，其中重点在于 `runCode` 函数，从函数名就能看出它的作用是运行代码。在这个函数中，关键语句是：

```java
String runResult = executeStringService.executeString(source, systemIn);
```

通过调用 `executeStringService` 的 `executeString` 方法来获取代码执行后的结果。因此，需要查找 `executeString` 方法的具体实现。

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

在 Java 中，`DiagnosticCollector` 是一个诊断收集器，用于收集编译过程中产生的诊断信息，例如编译错误、警告等。可以将它传递给编译器，然后在编译完成后，若有错误，就可以从中获取错误信息，进而直接返回给用户。

#### StringCompiler.compile(code, diagnostics)

这个函数用于将源代码的字符串动态编译为字节数组。

> **什么是动态编译**

在运行时编译 Java 代码，并通过类加载器将编译好的类加载进 JVM。这种在运行时编译代码的操作称为动态编译。

> **为什么使用动态编译呢？**

通过使用动态编译，可以直接将源代码的字符串编译为字节码，而不需要中间生成 .java 和 .class 文件。在没有动态编译之前，如果想要在运行过程中编译 Java 源代码，需要将源代码写入一个 .java 文件，然后通过 javac 编译这个文件，得到 .class 文件，最后将 .class 文件通过 ClassLoader 加载进内存，才能得到 Class 对象。这个过程存在两个问题：一是会生成 .java 和 .class 两个文件，运行之后还要将它们删除，以防止污染服务器环境；二是涉及到文件操作，这种 IO 操作相对耗时。因此，使用 Java 的动态编译技术可以直接跳过文件生成的过程，将源代码字符串直接编译为字节码的字节数组。这样既不会污染环境，又避免了额外的 IO 操作，从而具有更高的效率和便利性。

因此，首先需要获取编译器：

```java
// 获取系统编译器
JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
```

执行编译操作：

```java
Boolean result = compiler.getTask(null, javaFileManager, diagnostics,
        null, null, Arrays.asList(tmpJavaFileObject)).call();
```

关键在于`compiler.getTask`，看一下他需要哪些参数

```java
JavaCompiler.CompilationTask getTask(Writer out,
        JavaFileManager fileManager,
        DiagnosticListener<? super JavaFileObject> diagnosticListener,
        Iterable<String> options,
        Iterable<String> classes,
        Iterable<? extends JavaFileObject> compilationUnits)
```

需要6个参数：

- `out`：一个 `Writer` 对象，用于输出编译过程中的消息。通常用于打印编译过程中的信息，比如编译警告和错误。

- `fileManager`：一个 `JavaFileManager` 对象，用于管理编译过程中涉及的文件。它提供了一种方式来访问文件系统，以及对内存中文件的支持。编译器使用它来读取源文件和写入生成的类文件。

- `diagnosticListener`：一个 `DiagnosticListener` 对象，用于监听编译过程中产生的诊断信息，如编译错误、警告等。它通常用于收集和处理编译过程中的诊断信息。

- `options`：一个字符串的迭代器，表示编译选项。这些选项用于控制编译器的行为，例如指定编译版本、设置编译路径等。

- `classes`：一个字符串的迭代器，表示要编译的类的名称。这通常用于增量编译，指定哪些类需要重新编译。

- `compilationUnits`：一个 `JavaFileObject` 对象的迭代器，表示要编译的源文件单元。每个源文件单元可以是一个源代码文件、一个已经编译的类文件或者一个内存中的文件。

下面按照参数顺序来讲解：

###### out

上面提到的这个参数用于输出编译过程中的警告和错误。在之前已经使用了一个 `DiagnosticListener` 对象来监听编译过程中产生的警告和错误，因此在这里可以将该参数设置为 `null`。

###### fileManager

`JavaFileManager` 是 Java 编译器用来管理源文件和类文件的接口。它提供了一种抽象机制，使编译器能够与不同的文件系统进行交互，包括文件系统、ZIP 文件、内存中的文件等。通过实现 `JavaFileManager` 接口，可以自定义编译过程中的文件管理行为，例如指定源文件的搜索路径、控制编译后的类文件的输出位置等。在这里，并不直接实现 `JavaFileManager`，否则就需要重写很多方法。`ForwardingJavaFileManager` 是 Java 编译器提供的一个便捷的抽象类，用于简化自定义 `JavaFileManager` 的实现。它实现了 `JavaFileManager` 接口，并提供了一些默认的行为，同时允许通过继承和重写方法来添加自定义行为。这个类的作用类似于装饰器模式，可以通过继承 `ForwardingJavaFileManager` 来扩展它，然后重写感兴趣的方法，而不需要重新实现 `JavaFileManager` 的所有方法。

这样只需要重写以下两个方法：

```java
public static class TmpJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
   protected TmpJavaFileManager(JavaFileManager fileManager) {
      super(fileManager);
   }

   @Override
   public JavaFileObject getJavaFileForInput(JavaFileManager.Location location,
                                             String className,
                                             JavaFileObject.Kind kind) throws IOException {
      JavaFileObject javaFileObject = fileObjectMap.get(className);
      if (javaFileObject == null) {
         return super.getJavaFileForInput(location, className, kind);
      }
      return javaFileObject;
   }

   @Override
   public JavaFileObject getJavaFileForOutput(JavaFileManager.Location location,
                                              String className,
                                              JavaFileObject.Kind kind,
                                              FileObject sibling) throws IOException {
      JavaFileObject javaFileObject = new TmpJavaFileObject(className, kind);
      fileObjectMap.put(className, javaFileObject);
      return javaFileObject;
   }
}
```

这段代码定义了一个名为 `TmpJavaFileManager` 的静态内部类，它继承自 `ForwardingJavaFileManager<JavaFileManager>`。

- 构造函数 `TmpJavaFileManager` 接受一个 `JavaFileManager` 对象作为参数，并将其传递给父类的构造函数 `super(fileManager)`，以便让父类能够处理文件管理的相关操作。
- 重写了 `getJavaFileForInput` 方法：这个方法在请求输入文件时被调用。在这个实现中，它首先尝试从 `fileObjectMap` 中获取与指定类名匹配的 Java 文件对象。如果找到了对应的对象，就返回该对象；否则，调用父类的 `getJavaFileForInput` 方法来获取默认的输入文件。
- 重写了 `getJavaFileForOutput` 方法：这个方法在请求输出文件时被调用。在这个实现中，它创建了一个新的 `TmpJavaFileObject` 对象，并将其添加到 `fileObjectMap` 中，以便后续引用。然后返回这个新创建的 Java 文件对象。

> `TmpJavaFileObject`是自己实现的，下边会讲到。`fileObjectMap`是一个 `Map` 类型的对象，用于存储类名与对应的 Java 文件对象之间的映射关系

通过自定义 `TmpJavaFileManager` 类并重写这两个方法，可以实现对编译过程中输入和输出文件的定制管理。

> **为什么定义为静态内部类？**

定义 `TmpJavaFileManager` 为静态内部类的原因是出于封装和组织代码的考虑。

1. **为什么是内部类**：
   - 内部类可以访问外部类的私有成员，这意味着 `TmpJavaFileManager` 可以直接访问外部类的私有静态成员 `fileObjectMap`，这样可以简化代码，并使得这两个类之间的关系更加密切。
   - 如果 `TmpJavaFileManager` 只在外部类中使用，并且不需要对外部类的实例进行引用，将其定义为内部类可以避免在包中引入额外的类。

2. **为什么是静态内部类**：
   - 静态内部类不需要依赖外部类的实例，它可以独立存在和创建。这意味着可以在没有外部类实例的情况下创建 `TmpJavaFileManager` 的实例，这样可以提高灵活性和可重用性。
   - 如果 `TmpJavaFileManager` 不依赖于外部类的实例状态，并且它的实例在整个应用程序中都具有相同的行为，将其定义为静态内部类可以更清晰地表达这一点，并且可以减少不必要的对象创建。

###### diagnosticListener

这个直接将上面申请的诊断信息收集器对象传入即可

###### options和classes

在 `JavaCompiler.CompilationTask` 接口中，`options` 和 `classes` 参数是用于指定编译任务的选项和类的名称的。

1. **options 参数**：这是一个 `Iterable<String>` 类型的参数，用于指定编译任务的选项。编译选项可以控制编译器的行为，例如编译版本、生成的类文件路径、编译器的输出等。通常情况下，编译选项是一些字符串，可以通过传递 `options` 参数来指定。

2. **classes 参数**：这也是一个 `Iterable<String>` 类型的参数，用于指定需要编译的类的名称。编译器会根据传递的类名称来编译对应的类文件。这些类名称通常是完全限定类名，例如 `com.example.MyClass`。可以将多个类名放在 `classes` 参数中，以便一次性编译多个类文件。

本项目无编译选项和多个类编译的需求，所以这两个直接传`NULL`即可。

###### compilationUnits

`compilationUnits` 参数是用于指定需要编译的编译单元的集合，它是一个可迭代对象，其中每个元素都是 `JavaFileObject` 或其子类的实例。在编译任务中，编译单元表示需要进行编译的源文件或其他类型的输入。编译器会对 `compilationUnits` 中的每个编译单元进行编译。

Java 类库并没有提供能直接使用的 `JavaFileObject`，所以要通过继承 `SimpleJavaFileObject` 来实现自己的 `JavaFileObject`。为了知道都需要重写 `SimpleJavaFileObject` 的哪些方法，首先需要看一下 `compiler.getTask(...).call()` 的执行流程，看看都需要用到什么方法。

1. **获取源码：** 编译器首先需要获取源码以进行编译。它调用 `JavaFileObject` 的 `getCharContent()` 方法来获取源码的字符序列 `CharSequence`。

2. **编译源码：** 编译器对获取的源码进行编译，生成字节码。这时，编译器需要将生成的字节码存储到一个合适的 `JavaFileObject` 中。

3. **自定义存储位置：** 由于编译器无法直接知道应该将字节码存储到哪个 `JavaFileObject` 中，这时就需要使用 `JavaFileManager` 接口中的 `getJavaFileForOutput()` 方法。在这个方法中，会创建一个自定义的 `JavaFileObject` 子类的实例，用于存储字节码。

4. **存储字节码：** 在自定义的 `JavaFileObject` 子类中，会使用 `ByteArrayOutputStream` 等方式创建一个容器，用于存储字节码。编译器会调用 `openOutputStream()` 方法来获取输出流对象，并将编译生成的字节码写入到这个输出流中。

5. **获取字节码：** 最后，可以通过自定义的方法（比如 `getCompiledBytes()`）来获取字节码的字节数组形式，以便进一步处理或存储。

所以，需要重写 `getCharContent()` 和`openOutputStream()` 方法，以及给出一个新方法`getCompiledBytes()`：

```java
public static class TmpJavaFileObject extends SimpleJavaFileObject {
   private String source;
   private ByteArrayOutputStream outputStream;

   /**
    * 构造用来存储源代码的JavaFileObject
    * 需要传入源码source，然后调用父类的构造方法创建kind = Kind.SOURCE的JavaFileObject对象
    */
   public TmpJavaFileObject(String name, String source) {
      super(URI.create("String:///" + name + Kind.SOURCE.extension), Kind.SOURCE);
      this.source = source;
   }

   /**
    * 构造用来存储字节码的JavaFileObject
    * 需要传入kind，即想要构建一个存储什么类型文件的JavaFileObject
    */
   public TmpJavaFileObject(String name, Kind kind) {
      super(URI.create("String:///" + name + Kind.SOURCE.extension), kind);
      this.source = null;
   }

   @Override
   public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
      if (source == null) {
         throw new IllegalArgumentException("source == null");
      }
      return source;
   }

   @Override
   public OutputStream openOutputStream() throws IOException {
      outputStream = new ByteArrayOutputStream();
      return outputStream;
   }

   public byte[] getCompiledBytes() {
      return outputStream.toByteArray();
   }
}
```

> **为什么需要两个构造函数？**

有两个构造方法是为了方便在不同的情况下创建 `TmpJavaFileObject` 对象。

1. **存储源代码的构造方法：**
   这个构造方法用于在编译器需要处理源代码时创建 `TmpJavaFileObject` 对象。它需要传入源代码内容，并创建一个 `JavaFileObject` 实例，其中 `Kind` 类型为 `SOURCE`，表示这个对象用于存储源代码。

2. **存储字节码的构造方法：**
   这个构造方法用于在编译器生成字节码后创建 `TmpJavaFileObject` 对象。它不需要传入源代码内容，只需要指定文件的类型，比如 `CLASS`。在这个构造方法内部，`source` 设置为 `null`，表示这个对象不存储源代码，而是用于存储编译生成的字节码。

###### 完整实现

最后，编译器实现如下，通过调用 `StringSourceCompiler.compile(String source)` 就可以得到字符串源代码 source 的编译结果。

```java
public class StringSourceCompiler {
   private static Map<String, JavaFileObject> fileObjectMap = new ConcurrentHashMap<>();

   public static byte[] compile(String source) {
      JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
      DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
      JavaFileManager javaFileManager =
              new TmpJavaFileManager(compiler.getStandardFileManager(collector, null, null));

      // 从源码字符串中匹配类名
      Pattern CLASS_PATTERN = Pattern.compile("class\\s+([$_a-zA-Z][$_a-zA-Z0-9]*)\\s*");
      Matcher matcher = CLASS_PATTERN.matcher(source);
      String className;
      if (matcher.find()) {
         className = matcher.group(1);
      } else {
         throw new IllegalArgumentException("No valid class");
      }

      // 把源码字符串构造成JavaFileObject，供编译使用
      JavaFileObject sourceJavaFileObject = new TmpJavaFileObject(className, source);

      Boolean result = compiler.getTask(null, javaFileManager, collector,
              null, null, Arrays.asList(sourceJavaFileObject)).call();

      JavaFileObject bytesJavaFileObject = fileObjectMap.get(className);
      if (result && bytesJavaFileObject != null) {
         return ((TmpJavaFileObject) bytesJavaFileObject).getCompiledBytes();
      }
      return null;
   }

   /**
    * 管理JavaFileObject对象的工具
    */
   public static class TmpJavaFileManager extends ForwardingJavaFileManager<JavaFileManager> {
      // ...
   }

   /**
    * 用来封装表示源码与字节码的对象
    */
   public static class TmpJavaFileObject extends SimpleJavaFileObject {
      // ...
   }
}
```

#### 创建Callable对象

在 Java 中，Callable 是一个接口，它允许在多线程环境中执行任务，并返回一个结果。与之相对的是 Runnable 接口，但 Runnable 的 run 方法不会返回结果。由于需要知道程序执行完的结果，因此采用 Callable 接口。在 Callable 接口的实现中，调用 `mainExecutor.execute(classBytes, systemIn)` 方法，以执行任务并返回结果。

接下来，看一下`mainExecutor.execute`方法的执行流程

1. 字节码修改
2. 字节码加载
3. 方法执行
4. 结果获取

##### 字节码修改

在日常开发中，通常会使用 `System.out` 来展示程序的运行结果，异常信息也会直接打印到控制台上供查看。然而，要让客户端能够获得他们想要运行的代码的运行结果，需要以与 IDE 中相同的方式来收集程序在标准输出（System.out）和标准错误输出（System.err）中的信息，并返回给客户端。

然而，标准输出设备是整个虚拟机进程全局共享的资源。虽然可以通过 `System.setOut()` 和 `System.setErr()` 方法将输出流重定向到自己定义的 `PrintStream` 对象上，但这在多线程环境下可能会导致混乱，因为可能会将其他线程的结果也收集了。此外，允许客户端程序随意调用 `System` 类的方法还存在安全隐患，例如客户端程序中调用了 `System.exit(0)` 等方法，这对服务器来说是非常危险的。

因此，考虑将程序中的 `System` 类替换成自己编写的 `CustomSystem` 类。这样一来，既可以收集客户端程序的运行结果，又可以将 `System` 类中的潜在危险调用改写成抛出异常，以禁止客户端程序进行危险操作。

###### System类替换

为了将客户端程序中对 `System` 的调用替换为对 `CustomSystem` 的调用，采用了一种高级的方法，即直接在字节码中进行修改。这需要一个字节码修改器，它完成以下流程：

1. 遍历字节码常量池，找到所有对 "java/lang/System" 的符号引用；
2. 将 "java/lang/System" 替换为 ".../CustomSystem"。

为了完成上述步骤，首先需要了解类文件的结构，这样才能确定类对 `System` 的符号引用的位置，并知道如何进行替换。其次，需要一个字节数组修改工具 `ByteUtils`，来帮助修改存储字节码的字节数组。这样，就可以在字节码级别上将 `System` 替换为 `CustomSystem`，而无需直接修改客户端发送的源代码字符串，使操作更加优雅和高效。

###### 类文件结构

Class 文件是一组以 8 位字节为基础单位的二进制流，其中各个数据项目严格按照顺序紧凑地排列在文件中，中间没有任何分隔符。Java 虚拟机规范定义了一种类似于 C 语言结构体的伪结构来存储数据，其中只包含两种数据类型：无符号数和表。

- **无符号数**：无符号数是基本数据类型，用于描述数字、索引引用、数量值或者 UTF-8 编码的字符串值。在 Class 文件中，无符号数可分为 `u1`、`u2`、`u4`、`u8`，分别代表 1 字节、2 字节、4 字节和 8 字节的无符号数。
- **表**：表是由多个无符号数或其他表构成的复合数据类型，通常以 `_info` 结尾。表用于存储常量池、字段信息、方法信息、属性信息等。

Class 文件的前 8 个字节包含了魔数和版本号。其中前 4 个字节是魔数，固定为 `0xCAFEBABE`，用于确定文件是否为一个有效的 Class 文件。接下来的 4 个字节包含了当前 Class 文件的版本号，其中第 5、6 个字节是次版本号，第 7、8 个字节是主版本号。这些版本号用于指示该文件的 Java 版本。

常量池是 Class 文件中与其他项目关联最多、占用空间最大的数据项目。它从文件的第 9 个字节开始，并且是 Class 文件中第一个出现的表类型数据项目。常量池的开始的两个字节，即第 9 和第 10 个字节，存储一个 `u2` 类型的数据，表示常量池中常量的数量 `cpc`（constant_pool_count）。

常量池中记录了代码中出现过的所有 token（如类名、成员变量名等）以及符号引用（如方法引用、成员变量引用等）。主要包括两大类常量：

1. **字面量**：接近于 Java 语言层面的常量概念，包括文本字符串和声明为 `final` 的常量值。
2. **符号引用**：以一组符号来描述所引用的目标，包括类和接口的全限定名，字段的名称和描述符，方法的名称和描述符等。

每一项常量都通过一个表来存储。目前共有 14 种常量类型，每种类型都有自己的结构。在这里，简要介绍两种常见的常量类型：`CONSTANT_Class_info` 和 `CONSTANT_Utf8_info`。

`CONSTANT_Class_info` 的存储结构如下：

```
... [ tag=7 ] [ name_index ] ...
... [  1位  ] [     2位    ] ...
```

其中，`tag` 是标志位，用于区分常量类型。当 `tag` 等于 7 时，表示接下来的这个表是一个 `CONSTANT_Class_info`。`name_index` 是一个索引值，指向常量池中的一个 `CONSTANT_Utf8_info` 类型的常量所在的索引值。`CONSTANT_Utf8_info` 类型常量一般用于描述类的全限定名、方法名和字段名。它的存储结构如下：

```
... [ tag=1 ] [ 当前常量的长度 len ] [ 常量的符号引用的字符串值 ] ...
... [  1位  ] [        2位        ] [         len位         ] ...
```

在本项目中，需要修改的是值为 `java/lang/System` 的 `CONSTANT_Utf8_info` 常量。因为在类加载过程中，虚拟机会将常量池中的“符号引用”替换为“直接引用”，而 `java/lang/System` 是用来寻找其方法的直接引用的关键所在。只需将 `java/lang/System` 修改为我们自定义类的全限定名，就可以在运行时将通过 `System.xxx` 调用的方法偷偷地替换为我们的方法。

由于需要修改的内容位于常量池中，因此介绍到了常量池为止。修改操作会调用`ByteUtils` ，具有以下几个功能：

1. 将字节数组转换为整数（byte to int）。
2. 将整数转换为字节数组（int to byte）。
3. 将字节数组转换为字符串（byte to String）。
4. 将字符串转换为字节数组（String to byte）。
5. 替换字节数组中的部分字节。

##### 实现字节码修改器

实现的基本流程：

1. 取出常量池中的常量个数 `cpc`；
2. 遍历常量池中的 `cpc` 个常量，检查 `tag = 1` 的 `CONSTANT_Utf8_info` 常量；
3. 找到存储的常量值为 `java/lang/System` 的常量，并将其替换为 `".../CustomSystem"`；
4. 因为只可能有一个值为 `java/lang/System` 的 `CONSTANT_Utf8_info` 常量，所以找到后可以立即返回修改后的字节码。

###### System 类

`System` 类是 Java 程序中的一个标准系统类，与 `Class` 类一样直接注册进虚拟机，也就是说，它是直接与虚拟机打交道的类。`System` 类实现了多个功能，包括：

- 控制台与程序之间的输入输出流的控制。
- 系统的初始化。
- 获取系统环境变量。
- 一些简单的对虚拟机的操作等。

`System` 类位于 `java.lang` 包中，作为 Java 语言的核心特性之一，它是一个不可被实例化的类，只有一个私有的空参构造函数来禁止其他类创建 `System` 实例：

```java
private System() {
        }
```

`System` 类中只有三个公有的属性，即标准输入流、标准输出流和标准错误流：

```java
public final static InputStream in = null;
public final static PrintStream out = null;
public final static PrintStream err = null;
```

这三个字段都是 `static final` 的，并且 `out` 和 `err` 都是 `PrintStream` 类型。`PrintStream` 是一个装饰者模式的实现，它可以为其他输出流添加功能，使其能够方便地打印各种数据值的表示形式。`PrintStream` 类的特点是不会抛出 `IOException`，而是将错误标记为 `true`，使得用户可以通过 `checkError()` 方法来查看是否产生了 `IOException`。

`PrintStream` 类中有许多 `print` 方法，这些方法会将要打印的内容写入到其所装饰的输出流中，通常通过调用 `PrintStream` 中的各种 `write` 方法来实现。由于 `PrintStream` 只装饰了一个输出流，但可能有多个线程要向这个输出流写入内容，因此在 `PrintStream` 中所有需要写入内容的地方都进行了同步处理。

```java
private void write(String s) {
        try {
synchronized (this) {
        ensureOpen();
        textOut.write(s);
        textOut.flushBuffer();
        charOut.flushBuffer();
        if (autoFlush && (s.indexOf('\n') >= 0))
        out.flush();
        }
        }
        catch (InterruptedIOException x) {
        Thread.currentThread().interrupt();
        }
        catch (IOException x) {
        trouble = true;
        }
        }
```

对 `PrintStream` 进行详细介绍是为了说明在本项目中，`System` 类中原有的 `PrintStream` 并不符合需求。原有的 `PrintStream` 主要用于将多个输出格式化后并写入到一个流中，但在本项目中，需要能够同时运行多个客户端程序，并将它们的标准输出打印到不同的流中。

因此，除了将 `System` 类重写为 `CustomSystem` 外，我们的 `CustomSystem` 类中的 `out` 和 `err` 属性需要一种特殊的装饰。首先，它本质上仍然需要是一个 `PrintStream`，这样才能使得我们的 `CustomSystem` 能够有效地伪装成 `System`。其次，它内部装饰的不是单一的流，而是多个流。换句话说，每一个调用 `CustomSystem` 中方法的线程都会创建一个新的流来存储输出结果。

因此，需要进行以下两个替换操作：

1. 将 `System` 替换为 `CustomSystem`。
2. 将 `CustomSystem` 中的 `PrintStream out` 和 `PrintStream err` 的本质替换为自己编写的 `CustomPrintStream` 实例。

###### CustomSystem

对于 `CustomSystem` 类，基本上可以仿照 `System` 类的写法进行修改，但需要做一些调整。首先，需要修改 `out` 和 `err` 两个字段的实际类型，将它们修改为我们自己编写的 `CustomPrintStream` 对象：

```java
public final static PrintStream out = new HackPrintStream();
public final static PrintStream err = out;
```

然后，需要添加两个方法，用于获取当前线程的输出流中的内容和关闭当前线程的输出流：

```java
public static String getBufferString() {
        return out.toString();
        }

public static void closeBuffer() {
        out.close();
        }
```

接下来，对于一些比较危险的方法，要禁止客户端调用，一旦客户端调用了这些方法，直接抛出异常。例如：

```java
public static void exit(int status) {
        throw new SecurityException("Use hazardous method: System.exit().");
        }
```

最后，对于一些不涉及系统的工具方法，可以按原样保留，直接在方法内部调用 `System` 类的方法即可。例如：

```java
public static void arraycopy(Object src, int srcPos, Object dest, int destPos, int length) {
        System.arraycopy(src, srcPos, dest, destPos, length);
        }
```

这样，`CustomSystem` 类就基本完成了。详细的实现可以查看 `CustomSystem.java` 文件。

###### CustomPrintStream

在实现 `CustomPrintStream` 类时，首先需要让它继承 `PrintStream` 类并重写 `PrintStream` 的所有公有方法。这是因为在 `CustomSystem` 类中，我们要通过一个 `PrintStream` 类型的引用来引用 `CustomPrintStream` 的实例，所以 `CustomPrintStream` 的实例需要能够伪装成一个 `PrintStream`。

接下来，重点在于 `CustomPrintStream` 的实现。我们需要让 `CustomPrintStream` 能够支持多个线程调用，并且能够将不同线程通过 `PrintStream` 打印到流中的内容输出到不同的流中，以避免多个线程的标准输出操作互相影响，从而解决并发问题。为了实现这一点，我们需要为每个线程创建一个 `OutputStream` 来保存运行结果，并将这个 `OutputStream` 封闭到线程中（这里我们采用了 `ByteArrayOutputStream` 类）。由于需要实现线程封闭，最适合的工具就是 `ThreadLocal`。因此，在 `CustomPrintStream` 类中，我们添加了以下字段来保存每个线程的标准输出流以及每个线程的标准输出写入过程是否抛出 `IOException`：

```java
private ThreadLocal<ByteArrayOutputStream> out;
private ThreadLocal<Boolean> trouble;
```

这样就能确保每个线程都有自己独立的输出流，并且能够在多线程环境下正确地处理标准输出的操作，避免出现并发问题。

在对 `CustomPrintStream` 进行修改后，需要重写其父类 `PrintStream` 中所有对流进行操作的方法。下面举几个例子，说明如何对父类的方法进行重写：

1. `ensureOpen` 方法：
   - 在 `PrintStream` 中的实现中，该方法用于确保流处于打开状态，如果流已关闭则抛出异常。
   - 重写时，需要注意，不是判断 `out` 是否为空，而是判断 `out.get()` 是否为空，因为 `out` 是 `ThreadLocal` 类型，需要通过 `get()` 方法获取实际的输出流。

2. `close` 方法：
   - 在 `PrintStream` 中的实现中，该方法用于关闭流，但需要考虑递归关闭的问题。
   - 重写时，只需关闭当前线程的输出流，并将该线程的输出流从 `ThreadLocal` 中移除即可。

3. `write` 方法：
   - 在 `PrintStream` 中的实现中，该方法用于将字节数组写入流中，需要确保流处于打开状态。
   - 重写时，需要确保当前线程的输出流处于打开状态，并且将字节数组写入到当前线程的输出流中。

按照以上方式对 `PrintStream` 中需要重写的方法进行重写，详细的实现可见 `CustomPrintStream.java` 文件。这样就能保证 `CustomPrintStream` 在多线程环境下正确地处理标准输出的操作，避免出现并发问题。

##### 字节码加载

首先要注意的是，绝对不能使用系统提供的应用程序类加载器来加载这个类，因为这个类加载器是唯一的。如果使用这个类加载器加载了我们的字节码，当客户端修改了源代码并重新提交运行时，应用程序类加载器会认为这个类已经加载过了，不会再次加载它。这意味着，除非重新启动服务器，否则我们将永远无法执行客户端提交的新代码。

为了能够让客户端提交的代码不修改类名就能随意修改，需要支持热加载。我们知道，两个类相等需要满足以下 3 个条件：

1. 同一个 .class 文件；
2. 被同一个虚拟机加载；
3. 被同一个类加载器加载。

前两个条件都不容易改变，因此只能着手第三个条件，即每次都新建一个类加载器来加载客户端提交的字节码。这就需要实现一个新的类加载器：CustomClassLoader。

然而，需要注意的是，只有来自客户端传来的类需要被多次加载，而这个类所调用的其他类库方法等我们仍然希望按照原有的双亲委派机制进行加载。换句话说，只有我们自己调用 CustomClassLoader 来加载类时，它才会将字节数组转换成 Class 对象。而当虚拟机调用它时，它仍会按照以前的规则使用 loadClass 方法来加载类。

为了将存储字节码的字节数组转换成 Class 对象，需要通过定义一个 loadByte 方法来开放 defineClass 方法。这样，当我们自己需要使用 CustomClassLoader 来加载类时，就可以显式调用 loadByte 方法，而虚拟机在需要 CustomClassLoader 时则会调用 loadClass 方法。

`CustomClassLoader`具体实现如下：

```java
public class CustomClassLoader extends ClassLoader{
   public Class loadByte(byte[] classBytes) {
      return defineClass(null, classBytes, 0, classBytes.length);
   }
}
```

> 在Java中，`defineClass` 是ClassLoader类的一个受保护方法，用于将字节数组转换为一个Class对象。其方法签名为：
>
> ```java
> protected final Class<?> defineClass(String name, byte[] b, int off, int len)
> ```
>
> 参数含义如下：
>
> - `name`：要定义的类的名称。
> - `b`：要定义的类的字节码数组。
> - `off`：数组中的起始偏移量。
> - `len`：要使用的字节数量。
>
> `defineClass(null, classBytes, 0, classBytes.length)` 将字节数组 `classBytes` 转换为一个类对象，没有指定类名。实际上将会使用字节码中的类名。ClassLoader会从字节码中提取类名，并使用该类名定义新的类。因此第一个参数为 `null`。`classBytes` 是包含类的字节码的字节数组，其偏移量为 0，长度为 `classBytes` 的长度。这样就创建了一个新的 Class 对象，表示与字节数组中的字节码对应的类。

使用我们新编写的类加载器，我们就能够通过以下两行代码，无数次地加载客户端要运行的类！

```java
CustomClassLoader classLoader = new CustomClassLoader();
        Class aClass = classLoader.loadByte(modifyBytes);
```

##### 方法执行

```java
Method method = aClass.getMethod("main", new Class[]{String[].class});
        method.invoke(null, new String[]{null});
```

在这段代码中，首先我们获取了加载进虚拟机的类（`clazz`）中名为`main`的方法。这是因为在Java程序中，如果想要直接运行一个类，需要该类中含有一个入口方法，即`public static void main(String[] args)`方法。我们通过反射机制获取这个方法。

接着，我们调用了`Method`类中的`invoke`方法来执行`main`方法。`invoke`方法需要两个参数：第一个参数是要调用方法的对象或者类，如果是静态方法，这个参数可以为null；第二个参数是方法的参数，以Object数组的形式传递。

在这里，由于`main`方法是一个静态方法，所以我们将第一个参数设为null。而`main`方法本身的参数是一个字符串数组（`String[]`），所以我们创建一个包含一个null元素的字符串数组作为参数传递给`main`方法。

这样，通过反射机制，我们就可以运行加载进虚拟机的类的`main`方法了。

##### 结果获取

```java
try {
        Method method = aClass.getMethod("main", new Class[]{String[].class});
        method.invoke(null, new String[]{null});
        } catch (NoSuchMethodException e) {
        e.printStackTrace();
        } catch (IllegalAccessException e) {
        e.printStackTrace();
        } catch (InvocationTargetException e) {
        e.getCause().printStackTrace(CustomSystem.err);
        }

        String res = CustomSystem.getBufferString();
        CustomSystem.closeBuffer();
        return res;
```

这段代码是一个尝试执行加载到虚拟机中的类的`main`方法，并捕获可能抛出的异常。具体来说：

1. 首先，通过反射获取了加载到虚拟机中的类（`aClass`）中的名为`main`的方法。这个方法是程序的入口方法。

2. 然后，使用反射的`invoke`方法调用了获取到的`main`方法。这里的`invoke`方法需要两个参数：第一个参数是要调用方法的对象或者类，如果是静态方法，这个参数可以为null；第二个参数是方法的参数，以Object数组的形式传递。

3. 接着，使用了多个`catch`块来捕获可能抛出的异常：
   - 如果在获取`main`方法时出现了`NoSuchMethodException`异常，则打印异常堆栈轨迹。
   - 如果在执行`main`方法时出现了`IllegalAccessException`异常，则打印异常堆栈轨迹。
   - 如果在执行`main`方法时出现了`InvocationTargetException`异常，则打印其原因的异常堆栈轨迹。`InvocationTargetException`是由于在调用目标方法时发生异常而导致的异常。在这里，我们获取其原因（通过`getCause()`方法），然后将其异常堆栈轨迹输出到自定义的错误输出流（`CustomSystem.err`）中。

4. 在执行完`main`方法后，获取了自定义的缓冲区字符串（通过`CustomSystem.getBufferString()`方法），并关闭了缓冲区（通过`CustomSystem.closeBuffer()`方法），最后将缓冲区字符串返回。

#### Future 对象

我们并不知道客户端发来的程序的实际运行时间，出于安全的角度考虑，我们需要对其运行时间进行限制。通过使用 Callable + Future 的方式来限制程序的执行时间，并且对运行过程中可能出现的错误进行 catch，返回给客户端。

```java
Future<String> res = null;
        try {
        res = executorService.submit(runTask);
        } catch (RejectedExecutionException e) {
        return WAIT_WARNING;
        }

        String runResult;
        try {
        runResult = res.get(RUN_TIME_LIMIT, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
        runResult = "Program interrupted.";
        } catch (ExecutionException e) {
        runResult = e.getCause().getMessage();
        } catch (TimeoutException e) {
        runResult = "Time Limit Exceeded.";
        } finally {
        res.cancel(true);
        }
```

这段代码的作用是使用线程池执行一个任务，并获取任务的执行结果。

首先，通过`executorService.submit(runTask)`方法提交一个任务（`runTask`）给线程池执行，并将返回的`Future<String>`对象赋给变量`res`。`submit`方法会立即返回一个`Future`对象，用于跟踪任务的执行状态和结果。

然后，通过`try`块捕获可能抛出的`RejectedExecutionException`异常。如果任务无法被接受执行，即线程池拒绝了任务，说明此时提交的任务较多，那么就返回一个表示等待警告的字符串（`WAIT_WARNING`）。

接着，通过`res.get(RUN_TIME_LIMIT, TimeUnit.SECONDS)`方法获取任务的执行结果。这里使用了`get`方法，它会阻塞当前线程，直到任务执行完成并返回结果，或者超时时间达到。`RUN_TIME_LIMIT`是设定的任务执行时间限制，使用`TimeUnit.SECONDS`指定时间单位。

在`get`方法的异常处理中，有几种情况：

- 如果任务执行过程中被中断，则将`runResult`设为"Program interrupted."；
- 如果任务执行过程中出现异常，则将`runResult`设为异常的消息（通过`e.getCause().getMessage()`获取异常的原因消息）；
- 如果任务执行超时，则将`runResult`设为"Time Limit Exceeded."。

最后，在`finally`块中，调用`res.cancel(true)`取消任务的执行。参数`true`表示尝试中断任务的执行，即使它已经开始执行了。

#### 结果返回

```java
return runResult != null ? runResult : NO_OUTPUT;
```

这样就确保了无论客户端程序是否有输出，都能够返回相应的结果给客户端。

## 项目部署

#### 基本思路

采用CI/CD的思想来实现项目部署

CI/CD 是指持续集成（Continuous Integration）和持续交付/持续部署（Continuous Delivery/Continuous Deployment）的缩写。

1. **持续集成（Continuous Integration，CI）**：指在软件开发过程中，开发人员频繁地将代码集成到共享仓库（如版本控制系统）中，并通过自动化构建、测试和静态代码分析等工具，对新提交的代码进行验证和检查，以确保代码的质量和稳定性。

2. **持续交付/持续部署（Continuous Delivery/Continuous Deployment，CD）**：指在持续集成的基础上，自动化地将通过验证的代码部署到生产环境或者准生产环境中。

![1](pics\1.jpg)

项目代码托管在 GitHub 上。在开发过程中，首先需要拉取最新的代码，并解决可能出现的冲突。然后，针对新功能的开发通常会在主分支的基础上创建一个新的分支，在该分支上进行开发工作。开发完成并通过测试后，将新功能的分支合并到主分支中。Jenkins 用于对项目进行编译打包并创建镜像。创建镜像的方法有两种：

方式一：将本地微服务打包后上传到服务器，并编写 Dockerfile 文件完成镜像构建。

方式二：使用 dockerfile-maven-plugin 插件，直接将微服务创建为镜像，更加便捷。

本项目选择方式二来创建镜像，并在服务器上运行容器，提供服务。

#### dockerfile-maven-plugin 插件

要使用这个插件，需要再pom.xml文件中引入依赖

```java
<plugin>
                <groupId>com.spotify</groupId>
                <artifactId>dockerfile-maven-plugin</artifactId>
                <version>1.3.6</version>
                <configuration>
                    <repository>docker_storage/${lowercaseArtifactId}</repository>
                    <buildArgs>
                        <JAR_FILE>target/${project.build.finalName}.jar</JAR_FILE>
                    </buildArgs>
                </configuration>
            </plugin>
```

其中，"repository" 指定了 Docker 镜像的仓库名称，需要注意名称必须全部使用小写字母。而 "buildArgs" 则可用于指定一个或多个变量，这些变量将传递给 Dockerfile，在 Dockerfile 中可以通过 ARG 指令进行引用。

编写Dockerfile文件，放于pom.xml同级目录

```dockerfile
# 设置JAVA版本
FROM openjdk:8
# 指定存储卷, 任何向/tmp写入的信息都不会记录到容器存储层
VOLUME /tmp
# 拷贝运行JAR包
ARG JAR_FILE
COPY ${JAR_FILE} app.jar
# 设置JVM运行参数， 这里限定下内存大小，减少开销
ENV JAVA_OPTS="\
-server \
-Xms256m \
-Xmx512m \
-XX:MetaspaceSize=256m \
-XX:MaxMetaspaceSize=512m"
#空参数，方便创建容器时传参
ENV PARAMS=""
# 入口点， 执行JAVA运行命令
ENTRYPOINT ["sh","-c","java -jar $JAVA_OPTS /app.jar $PARAMS"]
```

#### 安装私有仓库

在持续集成环境的配置中，Jenkins需要发布大量的微服务，并与多台机器进行交互。一种方法是利用 Docker 镜像的保存与导出功能结合 SSH 实现，但这种方法交互繁琐，稳定性差，并且不便管理。因此，可以通过搭建 Docker 的私有仓库来解决这个问题。这个私有仓库类似于 GIT 仓库，可以集中统一管理资源，客户端可以从私有仓库拉取或更新所需的镜像。

1. 下载最新Registry镜像

   ```sh
   docker pull registry:latest
   ```

2. 启动Registry镜像服务

   ```sh
   docker run -d -p 5000:5000 --name registry -v /usr/local/docker/registry:/var/lib/registry registry:latest
   ```

   映射5000端口； -v是将Registry内的镜像数据卷与本地文件关联， 便于管理和维护Registry内的数据。

3. 配置Docker客户端

   正常生产环境中使用， 要配置HTTPS服务， 确保安全，内部开发或测试集成的局域网环境，可以采用简便的方式， 不做安全控制。

   先确保持续集成环境的机器已安装好Docker客户端， 然后做以下修改：

   ```sh
   vi /lib/systemd/system/docker.service
   ```

   修改内容：

   ```sh
   ExecStart=/usr/bin/dockerd --insecure-registry 192.168.200.100:5000
   ```

   指向安装Registry的服务IP与端口。

   重启生效：

   ```sh
   systemctl daemon-reolad
   systemctl restart docker.service
   ```

#### 4.7.2 jenkins中安装插件

#### jenkins

在 Jenkins 中配置好 Git、Maven、SSH 远程连接以及 Docker 依赖后，创建一个自由风格的软件项目。在项目配置中指定 Git 仓库为项目所在的仓库。在构建过程中，添加`Invoke top-level Maven targets`，并执行以下命令：

```shell
clean install -Dmaven.test.skip=true dockerfile:build -f pom.xml
```

- `-Dmaven.test.skip=true`：跳过测试阶段
- `dockerfile:build`：启动 Dockerfile 插件来构建容器
- `-f pom.xml`：指定需要构建的文件为 pom.xml（必须是 pom 文件）

并添加`Execute shell`

```shell
image_tag=$docker_registry/docker_storage/$JOB_NAME
echo '================docker镜像清理================'
if [ -n  "$(docker ps -a -f  name=$JOB_NAME  --format '{{.ID}}' )" ]
 then
 #删除之前的容器
 docker rm -f $(docker ps -a -f  name=$JOB_NAME  --format '{{.ID}}' )
fi
 # 清理镜像
docker image prune -f 

# 创建TAG
docker tag docker_storage/$JOB_NAME $image_tag
echo '================docker镜像推送================'
# 推送镜像
docker push $image_tag
# 删除TAG
docker rmi $image_tag
echo '================docker tag 清理 ================'if [ -n  "$(docker ps -a -f  name=$JOB_NAME  --format '{{.ID}}' )" ]
 then
 #删除之前的容器
 docker rm -f $(docker ps -a -f  name=$JOB_NAME  --format '{{.ID}}' )
fi
 # 清理镜像
docker image prune -f 
 # 启动docker服务
docker run -d --net=host -e --name $JOB_NAME docker_storage/$JOB_NAME
```

添加`Execute shell script on remote host using ssh`

```shell
echo '================拉取最新镜像================'
docker pull $docker_registry/docker_storage/$JOB_NAME

echo '================删除清理容器镜像================'
if [ -n  "$(docker ps -a -f  name=$JOB_NAME  --format '{{.ID}}' )" ]
 then
 #删除之前的容器
 docker rm -f $(docker ps -a -f  name=$JOB_NAME  --format '{{.ID}}' )
fi
 # 清理镜像
docker image prune -f 

echo '===============启动容器================'
docker run -d   --net=host -e PARAMS="--spring.profiles.active=prod" --name $JOB_NAME $docker_registry/docker_storage/$JOB_NAME
```

最后进行构建即可，在服务器上即可查看相关的镜像和容器。

为了简化开发流程，可以在 Jenkins 中设置当 GitHub 仓库的主分支发生变动时自动触发构建。

