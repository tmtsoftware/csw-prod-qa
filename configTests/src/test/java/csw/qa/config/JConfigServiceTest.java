//package csw.qa.config;
//
//import akka.actor.ActorSystem;
//import akka.stream.ActorMaterializer;
//import csw.config.api.javadsl.IConfigService;
//import csw.config.api.models.ConfigData;
//import csw.config.api.models.ConfigId;
//import csw.config.client.javadsl.JConfigClientFactory;
//import csw.location.api.javadsl.ILocationService;
//import csw.location.client.ActorSystemFactory;
//import csw.location.client.javadsl.JHttpLocationServiceFactory;
//import org.junit.Test;
//
//import java.io.File;
//import java.nio.file.Path;
//import java.nio.file.Paths;
//import java.time.Instant;
//import java.util.Objects;
//
//@SuppressWarnings({"ConstantConditions", "OptionalGetWithoutIsPresent"})
//  public class JConfigServiceTest {
//  private Path path1 = new File("some/test1/TestConfig1").toPath();
//  private Path path2 = new File("some/test2/TestConfig2").toPath();
//
//  private ActorSystem actorSystem = ActorSystemFactory.remote();
//  private ActorMaterializer mat = ActorMaterializer.create(actorSystem);
//  private ILocationService clientLocationService = JHttpLocationServiceFactory.makeLocalClient(actorSystem, mat);
//
//  private String authStoreDir = "/tmp/config-cli/auth";
//  private Path authStorePath = Paths.get(authStoreDir);
//  FileAuthStore authStore = new csw.aas.native.scaladsl.FileAuthStore(authStorePath);
//
//  private IConfigService configService = JConfigClientFactory.adminApi(actorSystem, clientLocationService);
//
//  @Test
//  public void testNormal() throws Exception {
//    runTests(configService, false);
//  }
//
//  @Test
//  public void testWithAnnex() throws Exception {
//    runTests(configService, true);
//  }
//
//  // Run tests using the given config cs instance
//  private void runTests(IConfigService cs, boolean annex) throws Exception {
//     String contents1 = "Contents of some file...\n";
//     String contents2 = "New contents of some file...\n";
//     String contents3 = "Even newer contents of some file...\n";
//
//     String comment1 = "create comment";
//     String comment2 = "update 1 comment";
//     String comment3 = "update 2 comment";
//
//    System.out.println("Running Java tests with annex = " + annex);
//
//    if (cs.exists(path1).get()) cs.delete(path1, "some comment").get();
//    if (cs.exists(path2).get()) cs.delete(path2, "another comment").get();
//    // Add, then update the file twice
//    Instant date1 = Instant.now();
//    Thread.sleep(100);
//    ConfigId createId1 = cs.create(path1, ConfigData.fromString(contents1), annex, comment1).get();
//    ConfigId createId2 = cs.create(path2, ConfigData.fromString(contents1), annex, comment1).get();
//    Instant date1a = Instant.now();
//    Thread.sleep(100); // make sure date is different
//    ConfigId updateId1 = cs.update(path1, ConfigData.fromString(contents2), comment2).get();
//    Instant date2 = Instant.now();
//    Thread.sleep(100); // make sure date is different
//    ConfigId updateId2 = cs.update(path1, ConfigData.fromString(contents3), comment3).get();
//    Instant date3 = Instant.now();
//
//    // Check that we can access each version
//    assert(Objects.equals(cs.getLatest(path1).get().get().toJStringF(mat).get(), contents3));
//    assert(Objects.equals(cs.getActive(path1).get().get().toJStringF(mat).get(), contents1));
//    assert(cs.getActiveVersion(path1).get().get().equals(createId1));
//    assert(Objects.equals(cs.getById(path1, createId1).get().get().toJStringF(mat).get(), contents1));
//    assert(Objects.equals(cs.getById(path1, updateId1).get().get().toJStringF(mat).get(), contents2));
//    assert(Objects.equals(cs.getById(path1, updateId2).get().get().toJStringF(mat).get(), contents3));
////    assert(cs.getLatest(path2).get().map(_.toJStringF(mat).get()).get.toString == contents1);
//    assert(Objects.equals(cs.getById(path2, createId2).get().get().toJStringF(mat).get(), contents1));
//
//    assert(Objects.equals(cs.getByTime(path1, date1).get().get().toJStringF(mat).get(), contents1));
//    assert(Objects.equals(cs.getByTime(path1, date1a).get().get().toJStringF(mat).get(), contents1));
//    assert(Objects.equals(cs.getByTime(path1, date2).get().get().toJStringF(mat).get(), contents2));
//    assert(Objects.equals(cs.getByTime(path1, date3).get().get().toJStringF(mat).get(), contents3));
//
////    val historyList1 = cs.history(path1).get();
////    assert(historyList1.size == 3);
////    assert(historyList1.head.comment == comment3);
////    assert(historyList1(1).comment == comment2);
////
////    val historyList2 = cs.history(path2).get();
////    assert(historyList2.size == 1);
////    assert(historyList2.head.comment == comment1);
////    assert(historyList1(2).comment == comment1);
////
////    // Test Active file features;
////    assert(csClient.getActive(path1).get().get().toJStringF(mat).get() == contents1)
////;
////    cs.setActiveVersion(path1, updateId1, "Setting active version").get();
////    assert(csClient.getActive(path1).get().get().toJStringF(mat).get() == contents2);
////    assert(cs.getActiveVersion(path1).get().contains(updateId1))
////
////    cs.resetActiveVersion(path1, "Resetting active version").get();
////    assert(csClient.getActive(path1).get().get().toJStringF(mat).get() == contents3);
////    assert(cs.getActiveVersion(path1).get().contains(updateId2));
////
////    cs.setActiveVersion(path1, updateId2, "Setting active version").get();
////
////    // test list()
////    val list = cs.list().get();
////    list.foreach(i => println(i));
////
////    // Test delete
////    cs.delete(path1, "test delete");
////    assert(cs.getLatest(path1).get().isEmpty);
////    assert(cs.getById(path1, createId1).get().get().toString == contents1);
////    assert(cs.getById(path1, updateId1).get().get().toString == contents2);
////    assert(cs.getActive(path1).get().get().toString == contents3);
//  }
//
//}
