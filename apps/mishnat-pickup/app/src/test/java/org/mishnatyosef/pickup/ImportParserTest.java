package org.mishnatyosef.pickup;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class ImportParserTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private ImportParser.Bundle parse(String text) throws Exception {
        File work = new File(folder.getRoot(), "work" + System.nanoTime());
        return ImportParser.parse(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), work);
    }

    @Test public void textListIsParsed() throws Exception {
        ImportParser.Bundle bundle = parse("# הערה\nחלב | 3 | ליטר | טרי | מוצרי חלב\nלחם\n\nביצים | 2,5\n");
        assertEquals(1, bundle.lists.size());
        assertEquals(3, bundle.lists.get(0).items.size());
        ImportParser.Item milk = bundle.lists.get(0).items.get(0);
        assertEquals("חלב", milk.name);
        assertEquals("3", milk.quantity);   // לא "3.0"
        assertEquals("ליטר", milk.unit);
        assertEquals("מוצרי חלב", milk.category);
        assertEquals("1", bundle.lists.get(0).items.get(1).quantity);
        assertEquals("2.5", bundle.lists.get(0).items.get(2).quantity);
    }

    @Test(expected = Exception.class)
    public void invalidQuantityIsRejected() throws Exception {
        parse("חלב | שלוש\n");
    }

    @Test(expected = Exception.class)
    public void emptyTextIsRejected() throws Exception {
        parse("# רק הערות\n\n");
    }

    @Test public void jsonListIsParsed() throws Exception {
        String json = "{\"format\":\"mishnat-pickup-list\",\"version\":1,\"lists\":[{\"account\":{\"id\":\"a1\",\"name\":\"ורד\"},"
                + "\"order\":{\"id\":\"o1\",\"name\":\"נח\"},\"items\":[{\"id\":\"p1\",\"name\":\"בטטות\",\"quantity\":2}]}]}";
        ImportParser.Bundle bundle = parse(json);
        assertEquals("נח", bundle.lists.get(0).orderName);
        assertEquals("2", bundle.lists.get(0).items.get(0).quantity);
    }

    @Test(expected = Exception.class)
    public void unknownJsonFormatIsRejected() throws Exception {
        parse("{\"format\":\"other\",\"version\":1,\"lists\":[]}");
    }
}
