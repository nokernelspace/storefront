package org.springframework.context.support

import java.util.ArrayList
import kotlin.enums.EnumEntries
import kotlin.jvm.functions.Function1
import kotlin.jvm.internal.SourceDebugExtension
import org.springframework.context.ApplicationContextInitializer
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.Profiles

/** @deprecated */
@Deprecated(message = "Use BeanRegistrarDsl instead", replaceWith = @ReplaceWith(expression = "BeanRegistrarDsl", imports = ["org.springframework.beans.factory.BeanRegistrarDsl"]))
@SourceDebugExtension(["SMAP\nBeanDefinitionDsl.kt\nKotlin\n*S Kotlin\n*F\n+ 1 BeanDefinitionDsl.kt\norg/springframework/context/support/BeanDefinitionDsl\n*L\n1#1,1226:1\n244#1:1227\n258#1,3:1228\n244#1:1231\n258#1,3:1232\n244#1:1235\n258#1,3:1236\n244#1:1239\n258#1,3:1240\n244#1:1243\n258#1,3:1244\n244#1:1247\n258#1,3:1248\n244#1:1251\n258#1,3:1252\n244#1:1255\n258#1,3:1256\n244#1:1259\n258#1,3:1260\n244#1:1263\n258#1,3:1264\n244#1:1267\n258#1,3:1268\n244#1:1271\n258#1,3:1272\n244#1:1275\n258#1,3:1276\n244#1:1279\n258#1,3:1280\n244#1:1283\n258#1,3:1284\n244#1:1287\n258#1,3:1288\n244#1:1291\n258#1,3:1292\n244#1:1295\n258#1,3:1296\n244#1:1299\n258#1,3:1300\n244#1:1303\n258#1,3:1304\n244#1:1307\n258#1,3:1308\n244#1:1311\n258#1,3:1312\n244#1:1315\n258#1,3:1316\n244#1:1319\n258#1,3:1320\n244#1:1323\n258#1,3:1324\n244#1:1327\n258#1,3:1328\n244#1:1331\n258#1,3:1332\n244#1:1335\n258#1,3:1336\n244#1:1339\n258#1,3:1340\n244#1:1343\n258#1,3:1344\n244#1:1347\n258#1,3:1348\n244#1:1351\n258#1,3:1352\n244#1:1355\n258#1,3:1356\n244#1:1359\n258#1,3:1360\n244#1:1363\n258#1,3:1364\n244#1:1367\n258#1,3:1368\n244#1:1371\n258#1,3:1372\n244#1:1375\n258#1,3:1376\n244#1:1379\n258#1,3:1380\n244#1:1383\n258#1,3:1384\n244#1:1387\n258#1,3:1388\n244#1:1391\n258#1,3:1392\n244#1:1395\n258#1,3:1396\n244#1:1399\n258#1,3:1400\n244#1:1403\n258#1,3:1404\n244#1:1407\n258#1,3:1408\n*S KotlinDebug\n*F\n+ 1 BeanDefinitionDsl.kt\norg/springframework/context/support/BeanDefinitionDsl\n*L\n295#1:1227\n295#1:1228,3\n295#1:1231\n295#1:1232,3\n333#1:1235\n333#1:1236,3\n333#1:1239\n333#1:1240,3\n371#1:1243\n371#1:1244,3\n371#1:1247\n371#1:1248,3\n409#1:1251\n409#1:1252,3\n409#1:1255\n409#1:1256,3\n447#1:1259\n447#1:1260,3\n447#1:1263\n447#1:1264,3\n485#1:1267\n485#1:1268,3\n485#1:1271\n485#1:1272,3\n523#1:1275\n523#1:1276,3\n523#1:1279\n523#1:1280,3\n562#1:1283\n562#1:1284,3\n562#1:1287\n562#1:1288,3\n601#1:1291\n601#1:1292,3\n601#1:1295\n601#1:1296,3\n640#1:1299\n640#1:1300,3\n640#1:1303\n640#1:1304,3\n679#1:1307\n679#1:1308,3\n679#1:1311\n679#1:1312,3\n718#1:1315\n718#1:1316,3\n718#1:1319\n718#1:1320,3\n757#1:1323\n757#1:1324,3\n757#1:1327\n757#1:1328,3\n796#1:1331\n796#1:1332,3\n796#1:1335\n796#1:1336,3\n836#1:1339\n836#1:1340,3\n836#1:1343\n836#1:1344,3\n876#1:1347\n876#1:1348,3\n876#1:1351\n876#1:1352,3\n916#1:1355\n916#1:1356,3\n916#1:1359\n916#1:1360,3\n956#1:1363\n956#1:1364,3\n956#1:1367\n956#1:1368,3\n996#1:1371\n996#1:1372,3\n996#1:1375\n996#1:1376,3\n1036#1:1379\n1036#1:1380,3\n1036#1:1383\n1036#1:1384,3\n1076#1:1387\n1076#1:1388,3\n1076#1:1391\n1076#1:1392,3\n1117#1:1395\n1117#1:1396,3\n1117#1:1399\n1117#1:1400,3\n1158#1:1403\n1158#1:1404,3\n1158#1:1407\n1158#1:1408,3\n*E\n"])
public open class BeanDefinitionDsl internal constructor(init: (BeanDefinitionDsl) -> Unit,
      condition: (ConfigurableEnvironment) -> Boolean = { it: ConfigurableEnvironment ->
            true
         }
   ) :
   ApplicationContextInitializer<GenericApplicationContext> {
   private final val init: (BeanDefinitionDsl) -> Unit
   private final val condition: (ConfigurableEnvironment) -> Boolean

   @PublishedApi
   internal final val children: ArrayList<BeanDefinitionDsl>

   @PublishedApi
   internal final lateinit var context: GenericApplicationContext

   init {
      this.init = init
      this.condition = condition
      this.children = ArrayList<>()
   }

   public final val env: ConfigurableEnvironment
      public final get() {
         val var10000: ConfigurableEnvironment = this.context.getEnvironment()
         return var10000
      }


   public fun profile(profile: String, init: (BeanDefinitionDsl) -> Unit) {
      this.children.add(BeanDefinitionDsl(init, { it: ConfigurableEnvironment ->
         it.acceptsProfiles(Profiles.of(arrayOf(`$profile`)))
      }))
   }

   public fun environment(condition: (ConfigurableEnvironment) -> Boolean, init: (BeanDefinitionDsl) -> Unit) {
      // $VF: Couldn't be decompiled
      // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
      // java.lang.IllegalStateException: Anonymous class does not have Class Kotlin metadata
      //   at org.vineflower.kotlin.KotlinWriter.writeClassDefinition(KotlinWriter.java:742)
      //   at org.vineflower.kotlin.KotlinWriter.writeClass(KotlinWriter.java:309)
      //   at org.vineflower.kotlin.expr.KNewExprent.toJava(KNewExprent.java:178)
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent.wrapOperandString(FunctionExprent.java:770)
      //   at org.jetbrains.java.decompiler.modules.decompiler.exps.FunctionExprent.wrapOperandString(FunctionExprent.java:736)
      //
      // Bytecode:
      // 00: aload 1
      // 01: ldc "condition"
      // 03: invokestatic kotlin/jvm/internal/Intrinsics.checkNotNullParameter (Ljava/lang/Object;Ljava/lang/String;)V
      // 06: aload 2
      // 07: ldc "init"
      // 09: invokestatic kotlin/jvm/internal/Intrinsics.checkNotNullParameter (Ljava/lang/Object;Ljava/lang/String;)V
      // 0c: new org/springframework/context/support/BeanDefinitionDsl
      // 0f: dup
      // 10: aload 2
      // 11: new org/springframework/context/support/BeanDefinitionDsl$environment$beans$1
      // 14: dup
      // 15: aload 1
      // 16: invokespecial org/springframework/context/support/BeanDefinitionDsl$environment$beans$1.<init> (Ljava/lang/Object;)V
      // 19: checkcast kotlin/jvm/functions/Function1
      // 1c: invokespecial org/springframework/context/support/BeanDefinitionDsl.<init> (Lkotlin/jvm/functions/Function1;Lkotlin/jvm/functions/Function1;)V
      // 1f: astore 3
      // 20: aload 0
      // 21: getfield org/springframework/context/support/BeanDefinitionDsl.children Ljava/util/ArrayList;
      // 24: aload 3
      // 25: invokevirtual java/util/ArrayList.add (Ljava/lang/Object;)Z
      // 28: pop
      // 29: return
   }

   public open fun initialize(context: GenericApplicationContext) {
      this.context = context
      this.init(this)
      var var10000: BeanDefinitionDsl = this.children.iterator()
      val var2: java.util.Iterator = var10000

      while (var2.hasNext()) {
         var10000 = (BeanDefinitionDsl)var2.next()
         val child: BeanDefinitionDsl = var10000
         val var5: Function1 = var10000.condition
         val var10001: ConfigurableEnvironment = context.getEnvironment()
         if (var5(var10001) as java.lang.Boolean) {
            child.initialize(context)
         }
      }
   }

   @SourceDebugExtension(["SMAP\nBeanDefinitionDsl.kt\nKotlin\n*S Kotlin\n*F\n+ 1 BeanDefinitionDsl.kt\norg/springframework/context/support/BeanDefinitionDsl$BeanSupplierContext\n+ 2 BeanFactoryExtensions.kt\norg/springframework/beans/factory/BeanFactoryExtensionsKt\n*L\n1#1,1226:1\n64#2:1227\n*S KotlinDebug\n*F\n+ 1 BeanDefinitionDsl.kt\norg/springframework/context/support/BeanDefinitionDsl$BeanSupplierContext\n*L\n1186#1:1227\n*E\n"])
   public open class BeanSupplierContext(context: GenericApplicationContext) {
      @PublishedApi
      internal final val context: GenericApplicationContext

      init {
         this.context = context
      }
   }

   /** @deprecated */
   @Deprecated(message = "Use BeanRegistrarDsl instead")
   public enum class Role {
      APPLICATION,
      SUPPORT,
      INFRASTRUCTURE;

      @JvmStatic
      fun getEntries(): EnumEntries<BeanDefinitionDsl.Role> {
         $ENTRIES
      }
   }

   /** @deprecated */
   @Deprecated(message = "Use BeanRegistrarDsl instead")
   public enum class Scope {
      SINGLETON,
      PROTOTYPE;

      @JvmStatic
      fun getEntries(): EnumEntries<BeanDefinitionDsl.Scope> {
         $ENTRIES
      }
   }
}
