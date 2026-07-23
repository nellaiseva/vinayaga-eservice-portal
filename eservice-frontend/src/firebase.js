import { initializeApp } from "firebase/app";
import { getAuth } from "firebase/auth";

const firebaseConfig = {
    apiKey: "AIzaSyCoqLkhh7KvPWkgTHyc0kYWT62z_ZOESNo",
    authDomain: "e-service-portal-5a5c4.firebaseapp.com",
    projectId: "e-service-portal-5a5c4",
    storageBucket: "e-service-portal-5a5c4.firebasestorage.app",
    messagingSenderId: "325201963663",
    appId: "1:325201963663:web:706c0b8f41ea9d71131efa"
};

const app = initializeApp(firebaseConfig);

export const auth = getAuth(app);