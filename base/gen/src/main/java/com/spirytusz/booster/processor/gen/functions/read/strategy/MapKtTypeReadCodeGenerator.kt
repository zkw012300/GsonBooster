package com.spirytusz.booster.processor.gen.functions.read.strategy

import com.spirytusz.booster.processor.base.data.config.TypeAdapterClassGenConfig
import com.spirytusz.booster.processor.base.data.type.JsonTokenName
import com.spirytusz.booster.processor.base.data.type.KtType
import com.spirytusz.booster.processor.base.extensions.asTypeNameIgnoreVariant
import com.spirytusz.booster.processor.base.log.MessageLogger
import com.spirytusz.booster.processor.gen.const.Const.Naming.READER
import com.spirytusz.booster.processor.gen.extensions.firstChatUpperCase
import com.spirytusz.booster.processor.gen.extensions.flatten
import com.spirytusz.booster.processor.gen.extensions.getReadingTempFieldName
import com.spirytusz.booster.processor.gen.functions.read.strategy.base.AbstractKtTypeReadCodeGenerator
import com.squareup.kotlinpoet.CodeBlock

class MapKtTypeReadCodeGenerator(
    logger: MessageLogger,
    config: TypeAdapterClassGenConfig
) : AbstractKtTypeReadCodeGenerator(logger, config) {
    override fun enterExpectTokenBlock(
        codeBlockBuilder: CodeBlock.Builder,
        ktType: KtType,
        codegenHook: (CodeBlock.Builder, String) -> Unit
    ) {
        val tempFieldName = ktType.getReadingTempFieldName()
        val initializer = getMapInitializer(ktType)
        val keyGeneric = ktType.generics.first()
        val valueGeneric = ktType.generics[1]
        codeBlockBuilder.addStatement(
            "val $tempFieldName = $initializer<%T, %T>()",
            keyGeneric.asTypeNameIgnoreVariant(),
            valueGeneric.asTypeNameIgnoreVariant()
        )

        codeBlockBuilder.addStatement("$READER.beginObject()")

        codeBlockBuilder.beginControlFlow("while ($READER.hasNext())")

        val keyFieldName = "keyOf" + valueGeneric.flatten().firstChatUpperCase()
        codeBlockBuilder.addStatement("val $keyFieldName = $READER.nextName()")
        val genericReadCodeGenerator = KtTypeReadCodeGeneratorImpl(logger, config)
        val genericReadCodeBlock =
            genericReadCodeGenerator.generate(valueGeneric) { genericCodeBlockBuilder, genericTempFieldName ->
                genericCodeBlockBuilder.addStatement("$tempFieldName.put($keyFieldName, $genericTempFieldName)")
            }
        codeBlockBuilder.add(genericReadCodeBlock)

        codeBlockBuilder.endControlFlow()

        codeBlockBuilder.addStatement("$READER.endObject()")
        codegenHook.invoke(codeBlockBuilder, tempFieldName)
    }

    override fun enterNullTokenBlock(
        codeBlockBuilder: CodeBlock.Builder,
        ktType: KtType,
        codegenHook: (CodeBlock.Builder, String) -> Unit
    ) {
        when {
            ktType.nullable -> {
                codeBlockBuilder.addStatement("$READER.nextNull()")
                codegenHook.invoke(codeBlockBuilder, "null")
            }
            nullSafe -> {
                codeBlockBuilder.addStatement("$READER.skipValue()")
            }
            else -> {
                codeBlockBuilder.addStatement("$READER.beginObject()")
            }
        }
    }

    override fun enterOtherTokenBlock(
        codeBlockBuilder: CodeBlock.Builder,
        ktType: KtType,
        codegenHook: (CodeBlock.Builder, String) -> Unit
    ) {
        codeBlockBuilder.addStatement("$READER.beginObject()")
    }

    private fun getMapInitializer(ktType: KtType): String {
        return when (ktType.jsonTokenName) {
            JsonTokenName.MAP -> {
                "mutableMapOf"
            }
            JsonTokenName.JAVA_MAP -> {
                "java.util.LinkedHashMap"
            }
            else -> {
                throw IllegalStateException("Unexpected json token ${ktType.jsonTokenName}")
            }
        }
    }
}