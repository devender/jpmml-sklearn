/*
 * Copyright (c) 2016 Villu Ruusmann
 *
 * This file is part of JPMML-SkLearn
 *
 * JPMML-SkLearn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-SkLearn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-SkLearn.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.sklearn;

import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.Model;
import org.dmg.pmml.ModelStats;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMML;
import org.dmg.pmml.UnivariateStats;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelEncoder;
import org.jpmml.converter.Schema;
import org.jpmml.converter.WildcardFeature;
import org.jpmml.converter.mining.MiningModelUtil;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import sklearn.Transformer;

public class SkLearnEncoder extends ModelEncoder {

    private List<Model> transformers = new ArrayList<>();

    private Map<FieldName, UnivariateStats> univariateStats = new LinkedHashMap<>();


    @Override
    public PMML encodePMML(Model model) {

        if (this.transformers.size() > 0) {
            List<Model> models = new ArrayList<>(this.transformers);
            models.add(model);

            Schema schema = new Schema(null, Collections.<Feature>emptyList());

            model = MiningModelUtil.createModelChain(models, schema);
        }

        PMML pmml = super.encodePMML(model);

        DataDictionary dataDictionary = pmml.getDataDictionary();

        if (model.getModelStats() == null) {
            model.setModelStats(new ModelStats());
        }
        ModelStats modelStats = model.getModelStats();

        dataDictionary.getDataFields().parallelStream().forEach(
                dataField -> {
                    UnivariateStats univariateStats = getUnivariateStats(dataField.getName());

                    if (univariateStats != null) {
                        modelStats.addUnivariateStats(univariateStats);
                    }
                }
        );

        /*List<DataField> dataFields = dataDictionary.getDataFields();
        for (DataField dataField : dataFields) {
            UnivariateStats univariateStats = getUnivariateStats(dataField.getName());

            if (univariateStats == null) {
                continue;
            }


            modelStats.addUnivariateStats(univariateStats);
        }*/

        return pmml;
    }

    public void updateFeatures(List<Feature> features, Transformer transformer) {
        OpType opType;
        DataType dataType;

        try {
            opType = transformer.getOpType();
            dataType = transformer.getDataType();
        } catch (UnsupportedOperationException uoe) {
            return;
        }

        for (Feature feature : features) {

            if (feature instanceof WildcardFeature) {
                WildcardFeature wildcardFeature = (WildcardFeature) feature;

                updateType(wildcardFeature.getName(), opType, dataType);
            }
        }
    }

    public void updateType(FieldName name, OpType opType, DataType dataType) {
        DataField dataField = getDataField(name);

        if (dataField == null) {
            throw new IllegalArgumentException(name.getValue());
        }

        dataField.setOpType(opType);
        dataField.setDataType(dataType);
    }

    public DataField createDataField(FieldName name) {
        return createDataField(name, OpType.CONTINUOUS, DataType.DOUBLE);
    }

    public DerivedField createDerivedField(FieldName name, Expression expression) {
        return createDerivedField(name, OpType.CONTINUOUS, DataType.DOUBLE, expression);
    }

    public void renameFeature(Feature feature, FieldName renamedName) {
        FieldName name = feature.getName();

        DerivedField derivedField = getDerivedField(name);
        if (derivedField == null) {
            throw new IllegalArgumentException(name.getValue());
        }

        Map<FieldName, DerivedField> derivedFields = getDerivedFields();
        derivedFields.remove(name);

        try {
            Field field = Feature.class.getDeclaredField("name");

            if (!field.isAccessible()) {
                field.setAccessible(true);
            }

            field.set(feature, renamedName);
        } catch (ReflectiveOperationException roe) {
            throw new RuntimeException(roe);
        }

        derivedField.setName(renamedName);

        addDerivedField(derivedField);
    }

    public void addTransformer(Model transformer) {
        this.transformers.add(transformer);
    }

    public UnivariateStats getUnivariateStats(FieldName name) {
        return this.univariateStats.get(name);
    }

    public void putUnivariateStats(UnivariateStats univariateStats) {
        putUnivariateStats(univariateStats.getField(), univariateStats);
    }

    public void putUnivariateStats(FieldName name, UnivariateStats univariateStats) {
        this.univariateStats.put(name, univariateStats);
    }
}