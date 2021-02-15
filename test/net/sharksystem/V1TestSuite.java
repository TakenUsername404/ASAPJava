package net.sharksystem;

import net.sharksystem.asap.crypto.CryptoUsage;
import net.sharksystem.asap.engine.*;
import net.sharksystem.asap.protocol.PDUTests;
import net.sharksystem.asap.serialization.SerializationTests;
import net.sharksystem.asap.storage.StorageTests;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
        SerializationTests.class,
        BatchprocessorTest.class,
        BasisMethodsTests.class,
        Point2PointTests.class,
        Point2PointTests2.class,
        UsageExamples.class,
        CreateNewChannelFromOutsideTest.class,
        PDUTests.class,
        CryptoTests.class,
        StorageTests.class,
        LongerMessages.class,
        CryptoUsage.class,
        SharkComponentTests.class
})
public class V1TestSuite {

}
