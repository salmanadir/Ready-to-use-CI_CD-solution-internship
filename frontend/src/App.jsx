import './App.css'  
import "@fortawesome/fontawesome-free/css/all.min.css";

import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'  
import RepoSelectionPage from './page/RepoSelectionPage'  
import RepoAnalysisPage from "./page/RepoAnalysisPage";

  
const App = () => {  
  return (  
    <Router>  
      <div className="app">  
        <Routes>  
          <Route path="/" element={<RepoSelectionPage />} />  
          <Route path="/analysis/:repoId" element={<RepoAnalysisPage />} />
         
        </Routes>  
      </div>  
    </Router>  
  )  
}  
  
export default App