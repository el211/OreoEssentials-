const express = require('express');
const path = require('path');

const app = express();
const PORT = process.env.SERVER_PORT || process.env.PORT || 3000;

app.use(express.static(path.join(__dirname)));

app.listen(PORT, () => {
  console.log(`Frontend serving on port ${PORT}`);
});
