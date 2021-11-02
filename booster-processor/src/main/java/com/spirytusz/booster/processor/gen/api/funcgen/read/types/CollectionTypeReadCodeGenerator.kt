package com.spirytusz.booster.processor.gen.api.funcgen.read.types

import com.spirytusz.booster.processor.data.JsonTokenName
import com.spirytusz.booster.processor.data.PropertyDescriptor
import com.spirytusz.booster.processor.data.TypeDescriptor
import com.spirytusz.booster.processor.gen.api.funcgen.read.types.base.TypeReadCodeGenerator
import com.spirytusz.booster.processor.gen.const.Constants.READER
import com.spirytusz.booster.processor.gen.extension.asTypeName
import com.spirytusz.booster.processor.gen.extension.getReadingStoreTempFieldName
import com.spirytusz.booster.processor.gen.extension.getTypeAdapterFieldName
import com.squareup.kotlinpoet.CodeBlock

class CollectionTypeReadCodeGenerator : TypeReadCodeGenerator {

    override fun generate(codeBlock: CodeBlock.Builder, propertyDescriptor: PropertyDescriptor) {
        addCheckCodeRecursively(codeBlock, propertyDescriptor, propertyDescriptor.type, null)
    }

    private fun addCheckCodeRecursively(
        codeBlock: CodeBlock.Builder,
        propertyDescriptor: PropertyDescriptor,
        typeDescriptor: TypeDescriptor,
        parentTypeDescriptor: TypeDescriptor?
    ) {
        if (!typeDescriptor.isArray()) {
            addReadNonCollectionTypeCode(codeBlock, typeDescriptor, parentTypeDescriptor!!)
            return
        }

        // if
        codeBlock.beginControlFlow("if ($READER.peek() == JsonToken.NULL)")
        codeBlock.addStatement("$READER.nextNull()")
        if (typeDescriptor.nullable()) {
            if (parentTypeDescriptor == null) {
                codeBlock.addStatement("${propertyDescriptor.fieldName} = null")
            } else {
                val parentTypeReadingStoreFieldName =
                    parentTypeDescriptor.getReadingStoreTempFieldName()
                codeBlock.addStatement("$parentTypeReadingStoreFieldName.add(null)")
            }
        }
        codeBlock.endControlFlow()

        // else
        codeBlock.beginControlFlow("else")
        val readStoreFieldName = addTempStoreField(codeBlock, typeDescriptor)
        codeBlock.addStatement("$READER.beginArray()")
        codeBlock.beginControlFlow("while ($READER.hasNext())")
        val genericType = typeDescriptor.typeArguments.first()
        addCheckCodeRecursively(codeBlock, propertyDescriptor, genericType, typeDescriptor)
        codeBlock.endControlFlow()
        codeBlock.addStatement("$READER.endArray()")
        if (parentTypeDescriptor == null) {
            codeBlock.addStatement("${propertyDescriptor.fieldName} = $readStoreFieldName")
        } else {
            val parentTypeReadingStoreFieldName =
                parentTypeDescriptor.getReadingStoreTempFieldName()
            codeBlock.addStatement("$parentTypeReadingStoreFieldName.add($readStoreFieldName)")
        }
        codeBlock.endControlFlow()
    }

    private fun addReadNonCollectionTypeCode(
        codeBlock: CodeBlock.Builder,
        typeDescriptor: TypeDescriptor,
        parentTypeDescriptor: TypeDescriptor
    ) {
        // if
        codeBlock.beginControlFlow("if ($READER.peek() == JsonToken.NULL)")
        codeBlock.addStatement("$READER.nextNull()")
        if (typeDescriptor.nullable()) {
            val parentTypeReadingStoreFieldName =
                parentTypeDescriptor.getReadingStoreTempFieldName()
            codeBlock.addStatement("$parentTypeReadingStoreFieldName.add(null)")
        }
        codeBlock.endControlFlow()

        // else
        codeBlock.beginControlFlow("else")
        when {
            typeDescriptor.isPrimitive() -> addReadPrimitiveTypeCode(
                codeBlock,
                typeDescriptor,
                parentTypeDescriptor
            )
            typeDescriptor.isObject() -> addReadObjectTypeCode(
                codeBlock,
                typeDescriptor,
                parentTypeDescriptor
            )
            typeDescriptor.isEnum() -> addReadEnumTypeCode(
                codeBlock,
                typeDescriptor,
                parentTypeDescriptor
            )
        }
        codeBlock.endControlFlow()
    }

    private fun addReadPrimitiveTypeCode(
        codeBlock: CodeBlock.Builder,
        typeDescriptor: TypeDescriptor,
        parentTypeDescriptor: TypeDescriptor
    ) {
        val parentTypeReadingStoreFieldName = parentTypeDescriptor.getReadingStoreTempFieldName()
        val nextFunExp = typeDescriptor.jsonTokenName.nextFunExp
        codeBlock.addStatement("$parentTypeReadingStoreFieldName.add($READER.$nextFunExp)")
    }

    private fun addReadObjectTypeCode(
        codeBlock: CodeBlock.Builder,
        typeDescriptor: TypeDescriptor,
        parentTypeDescriptor: TypeDescriptor
    ) {
        val parentTypeReadingStoreFieldName = parentTypeDescriptor.getReadingStoreTempFieldName()
        val typeAdapterFieldName = typeDescriptor.getTypeAdapterFieldName()
        codeBlock.addStatement("$typeAdapterFieldName.read($READER)?.let { $parentTypeReadingStoreFieldName.add(it) }")
    }

    private fun addReadEnumTypeCode(
        codeBlock: CodeBlock.Builder,
        typeDescriptor: TypeDescriptor,
        parentTypeDescriptor: TypeDescriptor
    ) {
        val parentTypeReadingStoreFieldName = parentTypeDescriptor.getReadingStoreTempFieldName()
        val nextFunExp = typeDescriptor.jsonTokenName.nextFunExp
        val typeSimpleName = typeDescriptor.flattenSimple()
        codeBlock.addStatement("val enumName = $READER.$nextFunExp")
        codeBlock.addStatement("$parentTypeReadingStoreFieldName.add($typeSimpleName.valueOf(enumName))")
    }

    private fun addTempStoreField(
        codeBlock: CodeBlock.Builder,
        typeDescriptor: TypeDescriptor
    ): String {
        val readStoreFieldName = typeDescriptor.getReadingStoreTempFieldName()
        val initializer = if (typeDescriptor.jsonTokenName == JsonTokenName.LIST) {
            "mutableListOf"
        } else {
            "mutableSetOf"
        }
        val genericTypeName = typeDescriptor.typeArguments.first().asTypeName()
        codeBlock.addStatement("val $readStoreFieldName = $initializer<%T>()", genericTypeName)
        return readStoreFieldName
    }
}