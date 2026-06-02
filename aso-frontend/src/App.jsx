import { BrowserRouter, Routes, Route } from "react-router-dom";

import Login from "./pages/Login";
import Register from "./pages/Register";
import EmployeeLogin from "./pages/EmployeeLogin";
import ClientDashboard from "./pages/ClientDashboard";
import EmployeeDashboard from "./pages/EmployeeDashboard";

export default function App() {
  return (
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route path="/employee-login" element={<EmployeeLogin />} />
          <Route path="/client" element={<ClientDashboard />} />
          <Route path="/employee" element={<EmployeeDashboard />} />
        </Routes>
      </BrowserRouter>
  );
}