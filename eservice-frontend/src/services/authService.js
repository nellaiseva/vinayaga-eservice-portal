import axios from "axios";
import { API_URL } from "../config";

export const login = async (phoneNumber, password) => {
    const response = await axios.post(
        `${API_URL}/auth/login`,
        {
            phoneNumber,
            password
        }
    );

    return response.data;
};

export const checkCustomerAuthStatus = async (phoneNumber) => {
    const response = await axios.post(
        `${API_URL}/customer/auth/status`,
        {
            phoneNumber
        }
    );

    return response.data;
};

export const sendCustomerEnrollOtp = async (phoneNumber) => {
    const response = await axios.post(
        `${API_URL}/customer/enroll/send-otp`,
        {
            phoneNumber
        }
    );

    return response.data;
};

export const completeCustomerEnrollment = async (phoneNumber, otp, password) => {
    const response = await axios.post(
        `${API_URL}/customer/enroll/complete`,
        {
            phoneNumber,
            otp,
            password
        }
    );

    return response.data;
};

export const sendCustomerForgotOtp = async (phoneNumber) => {
    const response = await axios.post(
        `${API_URL}/customer/forgot-password/send-otp`,
        {
            phoneNumber
        }
    );

    return response.data;
};

export const resetCustomerPassword = async (phoneNumber, otp, newPassword) => {
    const response = await axios.post(
        `${API_URL}/customer/forgot-password/reset`,
        {
            phoneNumber,
            otp,
            newPassword
        }
    );

    return response.data;
};