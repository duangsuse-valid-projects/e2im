package org.duangsuse.attrtools;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class Ext2AttrTest {
    private Context mContext;
    private Ext2Attr mInstance;
    private File mTestFile;

    @Before
    public void setUp () throws IOException {
        mContext = InstrumentationRegistry.getTargetContext();
        mInstance = new Ext2Attr(mContext);
        mTestFile = new File(mContext.getExternalFilesDir(null)
                .getAbsolutePath() + "/test.file");
        if (mTestFile.exists()) {
            mTestFile.delete();
        }
        mTestFile.createNewFile();
    }

    @Test
    public void shouldFClose() {
        assertTrue(mInstance.connect());
        mInstance.close();
        assertTrue(mInstance.isNotConnected());
    }

    @Test
    public void shouldAConnect() {
        assertTrue(mInstance.connect() && !mInstance.isNotConnected());
    }

    @Test
    public void shouldBAddi() throws Ext2Attr.ShellException {
        assertTrue(mInstance.connect());
        assertEquals(mInstance.addi(mTestFile.getAbsolutePath()),
                Ext2Attr.RESULT_CHANGED);
        int queryResult = mInstance.query(mTestFile.getAbsolutePath());
        assertNotEquals(queryResult, Ext2Attr.ATTRIBUTE_A);
    }

    @Test
    public void shouldCSubi() throws Ext2Attr.ShellException {
        assertTrue(mInstance.connect());
        assertEquals(mInstance.subi(mTestFile.getAbsolutePath()),
                Ext2Attr.RESULT_CHANGED);
        int queryResult = mInstance.query(mTestFile.getAbsolutePath());
        assertEquals(queryResult, Ext2Attr.ATTRIBUTE_A);
    }

    @Test
    public void shouldEExecuteCommand() {
        assertTrue(mInstance.connect());
        String command = "echo Haoye";
        mInstance.executeCommand(command);
        String result = mInstance.stdout.nextLine();
        assertEquals(result, "Haoye");
    }
}