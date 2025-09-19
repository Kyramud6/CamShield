
    import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-app.js";
    import { 
      getFirestore, collection, addDoc, serverTimestamp, 
      query, orderBy, onSnapshot, deleteDoc, doc 
    } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js";

    // 🔹 Firebase Config
    const firebaseConfig = {
      apiKey: "AIzaSyDOaqfyMNW96fIeFxvXdZgzrnQ-bDZPgbU",
      authDomain: "camshield-50aec.firebaseapp.com",
      projectId: "camshield-50aec",
      storageBucket: "camshield-50aec.firebasestorage.app",
      messagingSenderId: "655472977777",
      appId: "1:655472977777:web:907fe040370301c08b78d0",
      measurementId: "G-W4N2BLCEL3"
    };

    const app = initializeApp(firebaseConfig);
    const db = getFirestore(app);

    const form = document.getElementById("incidentForm");
    const preview = document.getElementById("preview");
    const list = document.getElementById("incidentList");
    const fileInput = document.getElementById("incidentImage");

    // 📌 Image preview + size check
    fileInput.addEventListener("change", (e) => {
      const file = e.target.files[0];
      if (!file) return;

      if (file.size > 1024 * 1024) {
        alert("File size exceeds 1 MB");
        e.target.value = "";
        preview.innerHTML = "";
        return;
      }

      const reader = new FileReader();
      reader.onload = (event) => {
        preview.innerHTML = `<img src="${event.target.result}" alt="Preview">`;
      };
      reader.readAsDataURL(file);
    });

    // 📌 Add incident
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const title = document.getElementById("incidentTitle").value;
      const description = document.getElementById("incidentDescription").value;
      const postedBy = document.getElementById("postedBy").value;
      let picture = "";

      if (fileInput.files[0]) {
        const reader = new FileReader();
        reader.onload = async (event) => {
          picture = event.target.result;
          await saveIncident(title, description, picture, postedBy);
        };
        reader.readAsDataURL(fileInput.files[0]);
      } else {
        await saveIncident(title, description, picture, postedBy);
      }
    });

    async function saveIncident(title, description, picture, postedBy) {
      try {
        await addDoc(collection(db, "Incident"), {
          title,
          description,
          picture,
          postedBy,
          status: "pending",
          timestamp: serverTimestamp()
        });
        form.reset();
        preview.innerHTML = "";
        alert("Incident posted successfully!");
      } catch (e) {
        console.error("Error adding document: ", e);
      }
    }

    // 📌 Delete incident
    async function deleteIncident(id) {
      if (confirm("Are you sure you want to delete this incident?")) {
        try {
          await deleteDoc(doc(db, "Incident", id));
          alert("Incident deleted.");
        } catch (e) {
          console.error("Error deleting document: ", e);
        }
      }
    }
    window.deleteIncident = deleteIncident; // expose globally

    // 📌 Live feed
    const q = query(collection(db, "Incident"), orderBy("timestamp", "desc"));
    onSnapshot(q, (snapshot) => {
      list.innerHTML = "";
      snapshot.forEach((docSnap) => {
        const data = docSnap.data();
        list.innerHTML += `
          <li>
            <strong>${data.title}</strong><br>
            ${data.description || "No description"}<br>
            <small>
              Posted by: ${data.postedBy || "Unknown"} | 
              ${data.timestamp ? data.timestamp.toDate().toLocaleString() : "No time"}
            </small>
            ${data.picture ? `<br><img src="${data.picture}">` : ""}
            <br>
            <button onclick="deleteIncident('${docSnap.id}')">Delete</button>
          </li>
        `;
      });
    });