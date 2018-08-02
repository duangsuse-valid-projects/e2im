# Android Library E2IM

⛓ E2IM is Android library version of ChangeAttr

[![JetPack](https://jitpack.io/v/duangsuse/e2im.svg)](https://jitpack.io/#duangsuse/e2im)

# Usage
## Connect to Root Shell
Create an Ext2Attr instance:
```java
Ext2Attr instance = new Ext2Attr(context);
```
Connect to Root Shell:
```java
boolean success = instance.connect();
```
You can check the result from the return value.
## Query the "I" and "a" attributes
Once connected, you can query attributes via the `query(path)` method:
```java
try {
  int result = instance.query(path);
} catch (ShellException e) {
}
```
## Add the "I" attribute
Once connected, you can add the "I" attribute via the `addi(path)` method:
```java
try {
  int result = instance.addi(path);
} catch (ShellException e) {
}
```
## Remove the "I" attribute
Once connected, you can remove the "I" attribute via the `subi(path)` method:
```java
try {
  int result = instance.subi(path);
} catch (ShellException e) {
}
```
## Don't forget to close the shell
You can close the root shell by using the `close()` method:
```java
instance.close();
```


## Licenses

使用此项目请确保您遵守 `Apache License 2.0`

```
Copyright (C) 2017 duangsuse

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
