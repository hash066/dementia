import { useState, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Brain, Heart, Activity, Shield, Clock, Smartphone, Check, X, Database, Code, Server, Cpu, Sun, Moon, ArrowRight } from 'lucide-react';
import './App.css';

const scenarioNodes = {
  start: {
    text: "It's 2:00 AM. The front door sensor chimes. Your phone buzzes with a work email you forgot to send. You hear a loud crash in the kitchen.",
    options: [
      { text: "Check the front door", next: "door" },
      { text: "Run to the kitchen", next: "kitchen" },
      { text: "Grab your phone first", next: "email" }
    ],
    timeLimit: 6
  },
  door: {
    text: "The front door is wide open, but the street is empty. The kitchen goes dead silent. Your phone rings loudly, threatening to wake the whole house.",
    options: [
      { text: "Run into the dark street", next: "fail_street" },
      { text: "Sprint to the kitchen", next: "fail_kitchen" },
      { text: "Answer the phone", next: "fail_phone" }
    ],
    timeLimit: 4
  },
  kitchen: {
    text: "A glass is shattered on the floor. The back door is ajar. You step in a puddle of water. The front door alarm chimes again.",
    options: [
      { text: "Run out the back door", next: "fail_street" },
      { text: "Clean the glass so they don't step on it", next: "fail_glass" },
      { text: "Check the front door", next: "fail_door" }
    ],
    timeLimit: 4
  },
  email: {
    text: "You grab your phone. The moment you unlock it, you hear the front door slam shut. They are gone into the night.",
    options: [
      { text: "Run outside", next: "fail_street" },
    ],
    timeLimit: 3
  }
};

const EmpathyExercise = () => {
  const [currentNode, setCurrentNode] = useState('intro');
  const [timer, setTimer] = useState(0);

  useEffect(() => {
    let interval;
    if (currentNode !== 'intro' && !currentNode.startsWith('fail') && timer > 0) {
      interval = setInterval(() => {
        setTimer((prev) => prev - 1);
      }, 1000);
    } else if (timer === 0 && currentNode !== 'intro' && !currentNode.startsWith('fail')) {
      setCurrentNode('fail_time');
    }
    return () => clearInterval(interval);
  }, [currentNode, timer]);

  const startScenario = () => {
    setCurrentNode('start');
    setTimer(scenarioNodes['start'].timeLimit);
  };

  const handleOption = (next) => {
    setCurrentNode(next);
    if (scenarioNodes[next]) {
      setTimer(scenarioNodes[next].timeLimit);
    }
  };

  const resetExercise = () => {
    setCurrentNode('intro');
  };

  const isFail = currentNode.startsWith('fail');
  const nodeData = scenarioNodes[currentNode];

  return (
    <div className="glass-panel exercise-container">
      <AnimatePresence mode="wait">
        {currentNode === 'intro' && (
          <motion.div 
            key="intro"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -20 }}
            className="exercise-step"
          >
            <span className="badge">Interactive Scenario</span>
            <h3 className="exercise-question">The 2:00 AM Paradox</h3>
            <p style={{ color: 'var(--text-secondary)', marginBottom: '2rem', maxWidth: '500px' }}>
              Caregiving is a continuous sequence of impossible choices. Step into the shoes of a caregiver for just 15 seconds. Can you make the right call?
            </p>
            <button className="btn btn-primary" onClick={startScenario}>Start Simulation</button>
          </motion.div>
        )}

        {!isFail && currentNode !== 'intro' && (
          <motion.div 
            key={currentNode}
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            exit={{ opacity: 0 }}
            className="exercise-step"
          >
            <div style={{ width: '100%', height: '4px', background: 'var(--surface-border)', marginBottom: '2rem', borderRadius: '2px', overflow: 'hidden' }}>
              <motion.div 
                initial={{ width: '100%' }}
                animate={{ width: '0%' }}
                transition={{ duration: nodeData.timeLimit, ease: 'linear' }}
                style={{ height: '100%', background: timer <= 2 ? 'var(--accent-rust)' : 'var(--accent-sage)' }}
              />
            </div>
            <h3 className="exercise-question" style={{ fontSize: '1.25rem', lineHeight: '1.4' }}>
              {nodeData.text}
            </h3>
            <div className="exercise-options">
              {nodeData.options.map((opt, i) => (
                <button key={i} className="exercise-btn" onClick={() => handleOption(opt.next)}>
                  {opt.text}
                </button>
              ))}
            </div>
          </motion.div>
        )}

        {isFail && (
          <motion.div 
            key="failed"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            className="exercise-step"
          >
            <h3 className="exercise-question" style={{ color: 'var(--accent-rust)' }}>
              {currentNode === 'fail_time' ? "Time ran out." : "You can't be everywhere at once."}
            </h3>
            <p style={{ color: 'var(--text-secondary)', marginBottom: '2rem', maxWidth: '500px' }}>
              There is no "winning" this scenario alone. The overwhelming stress of managing impossible, rapidly compounding variables is the daily reality for dementia caregivers. That's why an autonomous system is crucial.
            </p>
            <button className="btn btn-secondary" onClick={resetExercise}>Try Again</button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
};

function App() {
  const [theme, setTheme] = useState('dark');

  useEffect(() => {
    document.body.setAttribute('data-theme', theme);
  }, [theme]);

  const toggleTheme = () => {
    setTheme(theme === 'dark' ? 'light' : 'dark');
  };

  return (
    <div className="app-container">
      <div className="ambient-glow glow-1"></div>
      <div className="ambient-glow glow-2"></div>

      <nav className="navbar">
        <div className="container nav-content">
          <div className="logo">
            <Brain className="logo-icon" size={28} />
            Dementor
          </div>
          <div className="nav-links">
            <a href="#features" className="nav-link">Features</a>
            <a href="#how-it-works" className="nav-link">How it works</a>
            <a href="#tech" className="nav-link">Tech Stack</a>
            <button onClick={toggleTheme} className="nav-link" style={{ background: 'none', border: 'none', display: 'flex', alignItems: 'center' }}>
              {theme === 'dark' ? <Sun size={20} /> : <Moon size={20} />}
            </button>
            <a href="/Dementor.apk" download="Dementor.apk" className="btn btn-primary" style={{ padding: '0.5rem 1.25rem', fontSize: '0.9rem' }}>
              Launch App <ArrowRight size={16} style={{ marginLeft: '6px' }}/>
            </a>
          </div>
        </div>
      </nav>

      <main>
        {/* Hero Section */}
        <section className="section hero">
          <div className="container">
            <motion.div 
              className="hero-content"
              initial={{ opacity: 0, y: 30 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 0.8, ease: "easeOut" }}
            >
              <span className="badge">Redefining Dementia Care</span>
              <h1 className="hero-title">
                The AI Companion That <br/>
                <span className="text-gradient-accent">Never Forgets.</span>
              </h1>
              <p className="hero-subtitle">
                An edge-native, continuous monitoring intelligence that empowers caregivers and protects loved ones. Local, private, and always on.
              </p>
              <div className="hero-actions">
                <a href="#solution" className="btn btn-primary">Discover the Solution</a>
                <a href="#problem" className="btn btn-secondary">Understand the Problem</a>
              </div>
            </motion.div>
            
            <motion.div 
              className="hero-image-wrapper"
              initial={{ opacity: 0, y: 50 }}
              animate={{ opacity: 1, y: 0 }}
              transition={{ duration: 1, delay: 0.2, ease: "easeOut" }}
            >
              <img src="/app-mockup.png" alt="Dementor App Interface" className="hero-image" />
              <div className="hero-image-overlay"></div>
            </motion.div>
          </div>
        </section>

        {/* Problem & Case Study */}
        <section id="problem" className="section">
          <div className="container problem-grid">
            <motion.div 
              className="problem-text"
              initial={{ opacity: 0, x: -30 }}
              whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6 }}
            >
              <h2>The Silent Epidemic of <span className="text-gradient">Caregiver Burnout</span></h2>
              <p>
                Dementia doesn't just affect the patient; it consumes the caregiver. Constant vigilance, anxiety over wandering, and managing complex medical histories lead to severe emotional and physical exhaustion.
              </p>
              <p>
                <strong>Real Case Study:</strong> In a stark investigative report by <a href="https://www.washingtonpost.com/investigations/interactive/2023/alzheimers-wandering-assisted-living/" target="_blank" rel="noopener noreferrer" style={{textDecoration: 'underline', color: 'var(--text-primary)'}}>The Washington Post</a>, it was revealed that over 2,000 dementia patients wandered from care facilities since 2018. The tragedy of Lynne Stewart, who walked out of a facility and suffered fatal hypothermia after alarms were ignored, highlights a systemic failure. The fragmentation of passive alarms, logs, and fragmented apps leaves caregivers and patients disastrously vulnerable.
              </p>
            </motion.div>
            <motion.div 
              className="glass-panel"
              initial={{ opacity: 0, x: 30 }}
              whileInView={{ opacity: 1, x: 0 }}
              viewport={{ once: true }}
              transition={{ duration: 0.6, delay: 0.2 }}
            >
              <div className="stat-box">
                <div className="stat-number">2,000+</div>
                <div className="stat-label">reported wandering incidents in care facilities since 2018 (Washington Post)</div>
              </div>
              <div className="stat-box">
                <div className="stat-number">6/10</div>
                <div className="stat-label">Alzheimer's patients will wander at least once</div>
              </div>
            </motion.div>
          </div>
        </section>

        {/* Empathy Exercise */}
        <section id="how-it-works" className="section exercise-section">
          <div className="container">
            <motion.div
              initial={{ opacity: 0, y: 30 }}
              whileInView={{ opacity: 1, y: 0 }}
              viewport={{ once: true }}
            >
              <EmpathyExercise />
            </motion.div>
          </div>
        </section>

        {/* Solution & Features */}
        <section id="features" className="section">
          <div className="container">
            <div className="features-header">
              <span className="badge">Our Solution</span>
              <h2>Intelligent. Private. Reliable.</h2>
              <p style={{ color: 'var(--text-secondary)' }}>
                Dementor consolidates caregiving into one seamless, AI-driven edge platform that anticipates needs before they become emergencies.
              </p>
            </div>
            
            <div className="features-grid">
              <motion.div className="feature-card glass-panel" whileHover={{ y: -5 }}>
                <div className="feature-icon"><Activity /></div>
                <h3>Real-Time Health Sync</h3>
                <p>Continuous monitoring via edge devices (ESP32/RPi) that streams vitals and motion data directly to your device without cloud latency.</p>
              </motion.div>
              <motion.div className="feature-card glass-panel" whileHover={{ y: -5 }}>
                <div className="feature-icon"><Brain /></div>
                <h3>Gemma Edge AI</h3>
                <p>On-device LLM intent classification using LiteRT. It understands complex caregiver queries locally, ensuring absolute privacy.</p>
              </motion.div>
              <motion.div className="feature-card glass-panel" whileHover={{ y: -5 }}>
                <div className="feature-icon"><Shield /></div>
                <h3>Wander Guard</h3>
                <p>Predictive spatial awareness that alerts caregivers the moment abnormal movement patterns are detected.</p>
              </motion.div>
              <motion.div className="feature-card glass-panel" whileHover={{ y: -5 }}>
                <div className="feature-icon"><Database /></div>
                <h3>Local Memory Layer</h3>
                <p>High-performance SQLite/FTS5 integration that instantly retrieves critical patient logs, medications, and historical context.</p>
              </motion.div>
              <motion.div className="feature-card glass-panel" whileHover={{ y: -5 }}>
                <div className="feature-icon"><Heart /></div>
                <h3>Emotional Analytics</h3>
                <p>Analyzes voice and text inputs to gauge caregiver burnout, suggesting interventions when stress levels peak.</p>
              </motion.div>
              <motion.div className="feature-card glass-panel" whileHover={{ y: -5 }}>
                <div className="feature-icon"><Smartphone /></div>
                <h3>Native Clean UI</h3>
                <p>Built with Jetpack Compose, the interface is deliberately minimalist, reducing cognitive load during high-stress moments.</p>
              </motion.div>
            </div>
          </div>
        </section>

        {/* Comparison Section */}
        <section className="section">
          <div className="container comparison-container">
            <div className="comparison-header">
              <h2>Why Dementor is Different</h2>
            </div>
            <div className="glass-panel" style={{ overflow: 'hidden' }}>
              <table className="comparison-table">
                <thead>
                  <tr>
                    <th>Feature</th>
                    <th>Traditional Apps</th>
                    <th className="ours">Dementor</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>Data Privacy</td>
                    <td>Cloud-dependent, risky</td>
                    <td className="ours">100% On-device processing</td>
                  </tr>
                  <tr>
                    <td>Latency</td>
                    <td>2-3 seconds API calls</td>
                    <td className="ours">Sub-millisecond edge response</td>
                  </tr>
                  <tr>
                    <td>Context Awareness</td>
                    <td>Basic keyword search</td>
                    <td className="ours">Gemma LLM Semantic Understanding</td>
                  </tr>
                  <tr>
                    <td>Ecosystem</td>
                    <td>Fragmented wearables</td>
                    <td className="ours">Unified ESP32/RPi integration</td>
                  </tr>
                  <tr>
                    <td>Offline Capable</td>
                    <td><X className="x-icon" size={20} /></td>
                    <td className="ours"><Check className="check-icon" size={20} /></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </section>

        {/* Tech Stack */}
        <section id="tech" className="section">
          <div className="container">
            <div className="features-header">
              <h2>Engineered for Scale</h2>
              <p style={{ color: 'var(--text-secondary)' }}>Built on a robust, production-ready architecture.</p>
            </div>
            <div className="tech-grid glass-panel">
              <div className="tech-item">
                <Code size={40} color="var(--accent-sage)" />
                <span>Kotlin / Compose</span>
              </div>
              <div className="tech-item">
                <Brain size={40} color="var(--accent-sand)" />
                <span>Gemma & LiteRT</span>
              </div>
              <div className="tech-item">
                <Cpu size={40} color="var(--accent-rust)" />
                <span>ESP32 / Raspberry Pi</span>
              </div>
              <div className="tech-item">
                <Server size={40} color="var(--text-primary)" />
                <span>Python Microservices</span>
              </div>
              <div className="tech-item">
                <Database size={40} color="var(--accent-sage)" />
                <span>SQLite FTS5</span>
              </div>
            </div>
          </div>
        </section>
      </main>

      <footer className="footer">
        <div className="container">
          <div className="footer-content">
            <div className="logo">
              <Brain className="logo-icon" size={24} />
              Dementor
            </div>
            <div className="nav-links">
              <a href="#" className="nav-link">Privacy Policy</a>
              <a href="#" className="nav-link">Terms of Service</a>
              <a href="#" className="nav-link">Contact</a>
            </div>
          </div>
          <div className="footer-bottom">
            &copy; {new Date().getFullYear()} Dementor Team. All rights reserved.
          </div>
        </div>
      </footer>
    </div>
  );
}

export default App;
