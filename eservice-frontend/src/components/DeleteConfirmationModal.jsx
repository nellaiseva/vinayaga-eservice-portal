import React, { useEffect } from "react";
import "./DeleteConfirmationModal.css";

function DeleteConfirmationModal({
    isOpen,
    title = "Delete Document",
    documentName = "",
    message = "Are you sure you want to delete this document? This will permanently remove the physical file and its database record. This action cannot be undone.",
    isDeleting = false,
    errorMessage = "",
    onConfirm,
    onCancel
}) {
    useEffect(() => {
        const handleKeyDown = (e) => {
            if (e.key === "Escape" && !isDeleting && isOpen) {
                onCancel();
            }
        };

        if (isOpen) {
            window.addEventListener("keydown", handleKeyDown);
        }

        return () => {
            window.removeEventListener("keydown", handleKeyDown);
        };
    }, [isOpen, isDeleting, onCancel]);

    if (!isOpen) return null;

    return (
        <div
            className="delete-modal-overlay"
            role="dialog"
            aria-modal="true"
            aria-labelledby="delete-modal-title"
            onClick={(e) => {
                if (e.target === e.currentTarget && !isDeleting) {
                    onCancel();
                }
            }}
        >
            <div className="delete-modal-card">
                <div className="delete-modal-icon-wrapper">
                    <span className="delete-modal-icon" role="img" aria-label="warning">⚠️</span>
                </div>

                <h3 id="delete-modal-title" className="delete-modal-title">
                    {title}
                </h3>

                {documentName && (
                    <div className="delete-modal-file-pill">
                        <span className="file-pill-icon">📄</span>
                        <span className="file-pill-name" title={documentName}>
                            {documentName}
                        </span>
                    </div>
                )}

                <p className="delete-modal-message">
                    {message}
                </p>

                {errorMessage && (
                    <div className="delete-modal-error" role="alert">
                        <span className="error-icon">⚠️</span>
                        <span>{errorMessage}</span>
                    </div>
                )}

                <div className="delete-modal-actions">
                    <button
                        type="button"
                        className="delete-modal-btn cancel-btn"
                        onClick={onCancel}
                        disabled={isDeleting}
                    >
                        Cancel
                    </button>
                    <button
                        type="button"
                        className="delete-modal-btn confirm-btn"
                        onClick={onConfirm}
                        disabled={isDeleting}
                    >
                        {isDeleting ? (
                            <>
                                <span className="btn-spinner" aria-hidden="true"></span>
                                Deleting...
                            </>
                        ) : (
                            "Delete File"
                        )}
                    </button>
                </div>
            </div>
        </div>
    );
}

export default DeleteConfirmationModal;
