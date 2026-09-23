import { useEffect, useState } from "react";
import axios from "axios";
//import { useParams } from "react-router-dom";
import {
    useParams,
    useNavigate,Link
} from "react-router-dom";
import "./ServiceFieldManager.css"
import { API_URL } from "../../config";
function ServiceFieldManager() {

    const { serviceId } = useParams();

    const [fields, setFields] =
        useState([]);

    const [fieldName, setFieldName] =
        useState("");

    const [fieldType, setFieldType] =
        useState("TEXT");

    const [requiredField,
        setRequiredField] =
        useState(true);

    const [editingId,
        setEditingId] =
        useState(null);
    const navigate =
        useNavigate();

    useEffect(() => {

        loadFields();

    }, []);

    const getAuthHeaders = () => {
        const token = localStorage.getItem("token");
        return token ? { headers: { Authorization: `Bearer ${token}` } } : {};
    };

    const loadFields = async () => {

        const response =
            await axios.get(
                `${API_URL}/service-form-fields/service/${serviceId}`,
                getAuthHeaders()
            );

        setFields(
            response.data
        );
    };

    const saveField = async () => {

        if (editingId) {

            await axios.put(
                `${API_URL}/service-form-fields/${editingId}`,
                {
                    serviceId,
                    fieldName,
                    fieldType,
                    requiredField
                },
                getAuthHeaders()
            );

        } else {

            await axios.post(
                (`${API_URL}/service-form-fields`),
                {
                    serviceId,
                    fieldName,
                    fieldType,
                    requiredField
                },
                getAuthHeaders()
            );
        }

        setFieldName("");
        setFieldType("TEXT");
        setRequiredField(true);
        setEditingId(null);

        loadFields();
    };
    const startEdit = (field) => {

        setEditingId(
            field.id
        );

        setFieldName(
            field.fieldName
        );

        setFieldType(
            field.fieldType
        );

        setRequiredField(
            field.requiredField
        );
    };
    const deleteField = async (id) => {

        if (
            !window.confirm(
                "Delete this field?"
            )
        ) {
            return;
        }

        await axios.delete(
            `${API_URL}/service-form-fields/${id}`,
            getAuthHeaders()
        );

        loadFields();
    };
    return (

        <div className="page-bg">

            <div className="field-manager-page">

                <button
                    onClick={() => navigate("/services")}
                    className="back-button"
                >
                    ← Back to Services
                </button>

                {/* Create / Edit Field */}

                <div className="field-card">

                    <h1 className="field-page-title">

                        Manage Service Fields

                    </h1>

                    <div className="field-form-grid">

                        <input
                            className="field-input"
                            placeholder="Field Name"
                            value={fieldName}
                            onChange={(e) =>
                                setFieldName(e.target.value)
                            }
                        />

                        <select
                            className="field-select"
                            value={fieldType}
                            onChange={(e) =>
                                setFieldType(e.target.value)
                            }
                        >

                            <option value="TEXT">

                                Text

                            </option>

                            <option value="NUMBER">

                                Number

                            </option>

                            <option value="DATE">

                                Date

                            </option>

                        </select>

                        <label className="required-checkbox">

                            <input
                                type="checkbox"
                                checked={requiredField}
                                onChange={(e) =>
                                    setRequiredField(
                                        e.target.checked
                                    )
                                }
                            />

                            Required

                        </label>

                        <div className="field-action-buttons">

                            <button
                                onClick={saveField}
                                className="save-field-button"
                            >

                                {

                                    editingId

                                        ?

                                        "Update Field"

                                        :

                                        "Add Field"

                                }

                            </button>

                            {

                                editingId && (

                                    <button

                                        onClick={() => {

                                            setEditingId(null);

                                            setFieldName("");

                                            setFieldType("TEXT");

                                            setRequiredField(true);

                                        }}

                                        className="cancel-field-button"

                                    >

                                        Cancel

                                    </button>

                                )

                            }

                        </div>

                    </div>

                </div>

                {/* Configured Fields */}

                <div className="configured-fields-card">

                    <div className="configured-fields-header">

                        <h2 className="configured-fields-title">

                            Configured Fields

                        </h2>

                    </div>

                    {

                        fields.length === 0 ?

                            (

                                <div className="empty-fields">

                                    No fields configured yet

                                </div>

                            )

                            :

                            fields.map(field => (

                                <div
                                    key={field.id}
                                    className="field-item"
                                >

                                    <div>

                                        <h4 className="field-name">

                                            {field.fieldName}

                                        </h4>

                                        <p className="field-meta">

                                            {field.fieldType}

                                            {" • "}

                                            {

                                                field.requiredField

                                                    ?

                                                    "Required"

                                                    :

                                                    "Optional"

                                            }

                                        </p>

                                    </div>

                                    <div className="field-buttons">

                                        <button
                                            onClick={() =>
                                                startEdit(field)
                                            }
                                            className="edit-field-button"
                                        >

                                            Edit

                                        </button>

                                        <button
                                            onClick={() =>
                                                deleteField(field.id)
                                            }
                                            className="delete-field-button"
                                        >

                                            Delete

                                        </button>

                                    </div>

                                </div>

                            ))

                    }

                </div>

            </div>

        </div>

    );

}

export default ServiceFieldManager;