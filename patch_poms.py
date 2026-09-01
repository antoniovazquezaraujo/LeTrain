def add_test_deps(pom_path):
    with open(pom_path, 'r') as f:
        content = f.read()
    test_deps = """
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.mockito</groupId>
            <artifactId>mockito-inline</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
"""
    content = content.replace('    </dependencies>', test_deps)
    with open(pom_path, 'w') as f:
        f.write(content)

add_test_deps('ui-terminal/pom.xml')
add_test_deps('ui-graphic/pom.xml')
