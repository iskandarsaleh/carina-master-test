
[![Carina - Getting started](https://raw.githubusercontent.com/qaprosoft/carina/master/docs/img/video.png)](https://youtu.be/UtY-KLFnmI8)

* Install and configure JDK 1.8+
* Install and configure [Apache Maven 3.5.2+](http://maven.apache.org/)
* Download and start the latest [Selenium standalone server](http://www.seleniumhq.org/download/)
* Download the latest version of [Eclipse](http://www.eclipse.org/downloads/) and install [TestNG plugin](http://testng.org/doc/download.html)

### Generating project
The easiest way to initialize new project is to use Carina archetype, you will get correct project structure along with test samples:
```
mvn archetype:generate -DarchetypeGroupId=com.qaprosoft \
                       -DarchetypeArtifactId=carina-archetype \
                       -DarchetypeVersion=1.5 \
                       -DgroupId=<your_groupId> \
                       -DartifactId=<your_artifactId> \
                       -Dname="<you_proj_name>" \
                       -Durl=<your_proj_url> \
                       -Dversion=<your_proj_version>
```
If any attribute contains spaces, it should be set in quotes (e.g.: -Dname="Hello World"). In above Maven command you have to specify 5 attributes while the first 3 should be left unchanged. Let's go through these attributes:

<table>
	<tr>
		<th>Attribute</th>
		<th>Meaning</th>
		<th>Example</th>
	</tr>
	<tr>
		<td>-DgroupId</td>
		<td>Company domain in reverce order</td>
		<td>com.qaprosoft</td>
	</tr>
	<tr>
		<td>-DartifactId</td>
		<td>Java project name</td>
		<td>carina-qa</td>
	</tr>
	<tr>
		<td>-Dname</td>
		<td>Name with more details</td>
		<td>"Carina Test Automation"</td>
	</tr>
	<tr>
		<td>-Durl</td>
		<td>Company URL</td>
		<td>http://qaprosoft.com</td>
	</tr>
	<tr>
		<td>-Dversion</td>
		<td>Project version</td>
		<td>1.0</td>
	</tr>
</table>

### Import to Eclipse
If generation is successfully complete, you would see a new project folder with a name equal to the artifactId attribute specified during generation, so navigate to that folder (where pom.xml is located) and execute the following Maven task:
```
mvn clean eclipse:eclipse
```
By executing this command, Maven should resolve all dependencies, downloading required libraries to your local repository and generating Eclipse classpath. Before importing new project to Eclipse, you should link your IDE to your Maven repository by executing the following task:
```
mvn -Dworkspace=<path_to_workspace> eclipse:configure-workspace
```
Here you have to specify the absolute path to the Eclipse workspace. After that, restart Eclipse IDE. Now you can import generated projects such as "Existing Java Project" into Eclipse IDE.

![Eclipse view](img/001-Initial-setup.png)
