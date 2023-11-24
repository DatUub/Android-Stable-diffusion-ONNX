package com.example.open.diffusion;

import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.open.diffusion.core.UNet;
import com.example.open.diffusion.core.tokenizer.EngTokenizer;
import com.example.open.diffusion.core.tokenizer.TextTokenizer;

import java.io.File;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ai.onnxruntime.OnnxTensor;

public class MainFragment extends Fragment {

    public static MainFragment newInstance() {

        Bundle args = new Bundle();

        MainFragment fragment = new MainFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private final ExecutorService exec = Executors.newCachedThreadPool();
    private final int[] resolution = {192, 256, 320, 384, 448, 512};

    private ImageView mImageView;
    private TextView mMsgView;
    private EditText mGuidanceView;
    private EditText mStepView;
    private EditText mPromptView;
    private EditText mNetPromptView;
    private Spinner mWidthSpinner;
    private Spinner mHeightSpinner;
    private EditText mSeedView;
    private long lastSeed = 0;
    private View mGesView;
    private View mSetView;
    private Spinner mSpinner;
    private View mSaveView;

    private UNet uNet;
    private Handler uiHandler;
    private TextTokenizer tokenizer;
    private boolean isWorking = false;

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            uNet.close();
            tokenizer.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uiHandler = new Handler();
        uNet = new UNet(getActivity(), Device.CPU);
        tokenizer = new EngTokenizer(getActivity());

        uNet.setCallback(new UNet.Callback() {
            @Override
            public void onUNetStep(int maxStep, int step) {
                uiHandler.post(new MyRunnable() {
                    @Override
                    public void run() {
                        mMsgView.setText(String.format("%d / %d", step + 1, maxStep));
                    }
                });
            }

            @Override
            public void onBuildImage(int status, Bitmap bitmap) {
                uiHandler.post(new MyRunnable() {
                    @Override
                    public void run() {
                        if (bitmap != null) mImageView.setImageBitmap(bitmap);
                    }
                });
            }

            @Override
            public void onComplete() {
                uiHandler.post(new MyRunnable() {
                    @Override
                    public void run() {
                        mMsgView.setText(getResources().getString(R.string.FragmentMain_Status_Done));
                    }
                });
            }

            @Override
            public void onStop() {

            }

            @Override
            public void onDecodeStep() {
                uiHandler.post(new MyRunnable() {
                    @Override
                    public void run() {
                        mMsgView.setText(getResources().getString(R.string.FragmentMain_Status_Decoding));
                    }
                });
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_main, container, false);

        mImageView = view.findViewById(R.id.image);
        mMsgView = view.findViewById(R.id.msg);
        mGuidanceView = view.findViewById(R.id.guidance);
        mStepView = view.findViewById(R.id.step);
        mPromptView = view.findViewById(R.id.prompt);
        mWidthSpinner = view.findViewById(R.id.width);
        mHeightSpinner = view.findViewById(R.id.height);
        mNetPromptView = view.findViewById(R.id.neg_prompt);
        mSeedView = view.findViewById(R.id.seed);
        mGesView = view.findViewById(R.id.generate);
        mSetView = view.findViewById(R.id.setting);
        mSpinner = view.findViewById(R.id.spinner);
        mSaveView = view.findViewById(R.id.save);

        mWidthSpinner.setSelection(3);
        mHeightSpinner.setSelection(3);

        setEnable(new File(PathManager.getModelPath(getActivity())).exists());
        Context context = getContext();

        view.findViewById(R.id.copy).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ProgressDialog progress = ProgressDialog.show(
                        context,
                        container.getResources().getString((R.string.FragmentMain_CopyModel_Working)),
                        "",
                        true,
                        false
                );
                exec.execute(new MyRunnable() {
                    @Override
                    public void run() {
                        try {
                            FileUtils.copyAssets(getActivity().getAssets(), "model", new File(PathManager.getAsssetOutputPath(getActivity())));
                        }catch (Exception e){
                            e.printStackTrace();
                        }finally {
                            uiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    setEnable(new File(PathManager.getModelPath(getActivity())).exists());
                                    progress.dismiss();
                                }
                            });
                        }
                    }
                });
            }
        });

        mSaveView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mImageView.getDrawable() != null) {
                    try {
                        HashMap<String, String> inferenceInputs = new HashMap<>();
                        inferenceInputs.put("prompt", mPromptView.getText().toString());
                        inferenceInputs.put("negative_prompt", mNetPromptView.getText().toString());
                        inferenceInputs.put("seed", Long.toString(lastSeed));
                        inferenceInputs.put("steps", mStepView.getText().toString());
                        inferenceInputs.put("guidance", mGuidanceView.getText().toString());
                        inferenceInputs.put("width", Integer.toString(resolution[mWidthSpinner.getSelectedItemPosition()]));
                        inferenceInputs.put("height", Integer.toString(resolution[mHeightSpinner.getSelectedItemPosition()]));
                        inferenceInputs.put(
                                "scheduler",
                                mSpinner.getSelectedItemPosition() == 0
                                    ? "EulerA"
                                    : "DPM"
                        );
                        boolean success = FileUtils.saveImage(
                                getActivity(),
                                FileUtils.getBitmap(mImageView.getDrawable()),
                                inferenceInputs
                        );
                        Toast.makeText(
                                getActivity(),
                                success
                                        ? getResources().getString(R.string.FragmentMain_Save_Success)
                                        : getResources().getString(R.string.FragmentMain_Save_Failed),
                                Toast.LENGTH_SHORT
                        ).show();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        });

        mGesView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Dismiss keyboard
                // TODO Solve this properly (focus remains on EditText component)
                View focusedView = getActivity().getWindow().getCurrentFocus();
                if (focusedView != null) {
                    InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
                }
                try {
                    if (isWorking) return;
                    isWorking = true;
                    mMsgView.setText(getResources().getString(R.string.FragmentMain_Status_Initializing));
                    exec.execute(createRunnable());
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        mSetView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().getSupportFragmentManager().beginTransaction().add(R.id.container, SettingFragment.newInstance()).addToBackStack(null).commit();
            }
        });


        return view;
    }

    private MyRunnable createRunnable(){
        final String guidanceText = mGuidanceView.getText().toString();
        final String stepText = mStepView.getText().toString();
        final String prompt = mPromptView.getText().toString();
        final String negPrompt = mNetPromptView.getText().toString();
        final String seedText = mSeedView.getText().toString();

        final int num_inference_steps = TextUtils.isEmpty(stepText) ? 8 : Integer.parseInt(stepText);
        final double guidance_scale = TextUtils.isEmpty(guidanceText) ? 5f : Float.valueOf(guidanceText);
        // TODO Indicate that 0 (and "") means a random seed will be selected in the UI
        // TODO Show selected seed in UI to simplify reproducing prompts (easier to compare model generations)
        lastSeed = TextUtils.isEmpty(seedText) ? 0 : Long.parseLong(seedText);
        if (lastSeed == 0) {
            lastSeed = new Random().nextLong();
        }
        final long seed = lastSeed;
        UNet.WIDTH = resolution[mWidthSpinner.getSelectedItemPosition()];
        UNet.HEIGHT = resolution[mHeightSpinner.getSelectedItemPosition()];

        return new MyRunnable() {
            @Override
            public void run() {
                try {
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMsgView.setText(getResources().getString((R.string.FragmentMain_Status_InitTokenizer)));
                        }
                    });
                    tokenizer.init();
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMsgView.setText(getResources().getString(R.string.FragmentMain_Status_TokenizingPrompts));
                        }
                    });
                    int batch_size = 1;
                    int[] textTokenized = tokenizer.encoder(prompt);
                    int[] negTokenized = tokenizer.createUncondInput(negPrompt);

                    OnnxTensor textPromptEmbeddings = tokenizer.tensor(textTokenized);
                    OnnxTensor uncondEmbedding = tokenizer.tensor(negTokenized);
                    float[][][] textEmbeddingArray = new float[2][tokenizer.getMaxLength()][768];

                    float[] textPromptEmbeddingArray = textPromptEmbeddings.getFloatBuffer().array();
                    float[] uncondEmbeddingArray = uncondEmbedding.getFloatBuffer().array();
                    for (int i = 0; i < textPromptEmbeddingArray.length; i++)
                    {
                        textEmbeddingArray[0][i / 768][i % 768] = uncondEmbeddingArray[i];
                        textEmbeddingArray[1][i / 768][i % 768] = textPromptEmbeddingArray[i];
                    }

                    OnnxTensor textEmbeddings = OnnxTensor.createTensor(App.ENVIRONMENT, textEmbeddingArray);
                    tokenizer.close();

                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMsgView.setText(getResources().getString(R.string.FragmentMain_Status_InitUNet));
                        }
                    });
                    uNet.init();
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMsgView.setText(getResources().getString(R.string.FragmentMain_Status_StartingInference));
                        }
                    });
                    uNet.inference(seed, num_inference_steps, textEmbeddings, guidance_scale, batch_size, UNet.WIDTH, UNet.HEIGHT, mSpinner.getSelectedItemPosition());
                }catch (Exception e){
                    uiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mMsgView.setText(getResources().getString((R.string.FragmentMain_Status_Error)));
                        }
                    });
                    e.printStackTrace();
                }finally {
                    isWorking = false;
                }
            }
        };
    }

    private void setEnable(boolean isEnable){
        mGesView.setEnabled(isEnable);
        mSetView.setEnabled(isEnable);
    }
}